package com.yishenghuang.heartext.tts

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.yishenghuang.heartext.data.BookFormat
import com.yishenghuang.heartext.data.BookRepository
import com.yishenghuang.heartext.data.EpubChapter
import com.yishenghuang.heartext.data.ReaderPreferences
import com.yishenghuang.heartext.data.TtsVoiceSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaybackSession(
    val active: Boolean = false,
    val bookId: String = "",
    val title: String = "",
    val author: String = "",
    val coverPath: String? = null,
    val chapterIndex: Int = 0,
    val chapterTitle: String = "",
    val chapterCount: Int = 0,
    val ttsMode: TtsMode = TtsMode.SYSTEM,
    val voiceLabel: String = "",
    val playbackState: TtsPlaybackState = TtsPlaybackState.Idle,
    val sentenceIndex: Int = 0,
    val sentenceCount: Int = 0,
    /** Chapter-local char range of the sentence currently being spoken. */
    val spokenStart: Int = 0,
    val spokenEnd: Int = 0,
    val message: String? = null
)

/**
 * App-scoped owner of listen/playback. Survives reader navigation; drives
 * [HearTextPlaybackService] + [TtsController] + [AudioFocusHelper].
 */
class PlaybackCoordinator(
    private val app: Application,
    private val scope: CoroutineScope,
    private val bookRepository: BookRepository,
    private val readerPreferences: ReaderPreferences,
    private val tts: TtsController
) {
    private val _session = MutableStateFlow(PlaybackSession())
    val session: StateFlow<PlaybackSession> = _session.asStateFlow()

    private var chapters: List<EpubChapter> = emptyList()
    private var voiceId: String? = null
    private var loadJob: Job? = null
    private var mirrorJob: Job? = null
    private var resumeAfterTransientLoss = false
    private var ducked = false
    private var serviceStarted = false
    @Volatile private var suppressAutoAdvance = false

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val focusHelper = AudioFocusHelper(app) { change ->
        when (change) {
            AudioFocusHelper.FocusChange.Gained -> onFocusGained()
            AudioFocusHelper.FocusChange.Lost -> {
                resumeAfterTransientLoss = false
                pauseFromFocus()
            }
            AudioFocusHelper.FocusChange.LostTransient -> {
                resumeAfterTransientLoss =
                    _session.value.playbackState == TtsPlaybackState.Speaking
                pauseFromFocus()
            }
            AudioFocusHelper.FocusChange.Duck -> onDuck()
        }
    }

    init {
        mirrorJob = scope.launch {
            launch {
                tts.state.collectLatest { state ->
                    _session.update { cur ->
                        if (!cur.active) cur else cur.copy(playbackState = state)
                    }
                    if (state == TtsPlaybackState.Idle && _session.value.active) {
                        maybeAdvanceChapter()
                    }
                }
            }
            launch {
                tts.message.collectLatest { msg ->
                    _session.update { cur -> if (!cur.active) cur else cur.copy(message = msg) }
                }
            }
            launch {
                tts.sentenceIndex.collectLatest { idx ->
                    _session.update { cur -> if (!cur.active) cur else cur.copy(sentenceIndex = idx) }
                }
            }
            launch {
                tts.sentenceCount.collectLatest { count ->
                    _session.update { cur -> if (!cur.active) cur else cur.copy(sentenceCount = count) }
                }
            }
            launch {
                tts.spokenStart.collectLatest { start ->
                    _session.update { cur -> if (!cur.active) cur else cur.copy(spokenStart = start) }
                }
            }
            launch {
                tts.spokenEnd.collectLatest { end ->
                    _session.update { cur -> if (!cur.active) cur else cur.copy(spokenEnd = end) }
                }
            }
        }
    }

    fun start(
        bookId: String,
        chapterIndex: Int,
        mode: TtsMode,
        voiceId: String?,
        voiceLabel: String = voiceLabelFor(mode),
        startSentenceIndex: Int = 0
    ) {
        loadJob?.cancel()
        loadJob = scope.launch {
            val book = bookRepository.getBook(bookId)
            if (book == null) {
                _session.update { it.copy(message = "Book not found") }
                return@launch
            }
            if (book.format == BookFormat.PDF) {
                _session.update { it.copy(message = "Listening is not available for PDF") }
                return@launch
            }
            val loaded = runCatching { bookRepository.loadChapterTexts(book) }.getOrElse { err ->
                _session.update { it.copy(message = err.message ?: "Unable to load chapters") }
                return@launch
            }
            if (loaded.isEmpty()) {
                _session.update { it.copy(message = "Nothing to read") }
                return@launch
            }
            chapters = loaded
            this@PlaybackCoordinator.voiceId = voiceId
            val index = chapterIndex.coerceIn(0, loaded.lastIndex)
            val chapter = loaded[index]
            if (!focusHelper.requestFocus()) {
                _session.update { it.copy(message = "Unable to get audio focus") }
                return@launch
            }
            tts.configure(mode, voiceId)
            tts.setSpeechRate(readerPreferences.settings.value.ttsSpeed)
            tts.setVolume(1f)
            ducked = false
            resumeAfterTransientLoss = false
            _session.value = PlaybackSession(
                active = true,
                bookId = bookId,
                title = book.title,
                author = book.author,
                coverPath = book.coverPath,
                chapterIndex = index,
                chapterTitle = chapter.title,
                chapterCount = loaded.size,
                ttsMode = mode,
                voiceLabel = voiceLabel,
                playbackState = TtsPlaybackState.Speaking,
                message = null
            )
            ensureService()
            tts.play(chapter.plainText, startSentenceIndex)
            persistProgress(bookId, index)
        }
    }

    fun startFromPreferences(
        bookId: String,
        chapterIndex: Int,
        startSentenceIndex: Int = 0
    ) {
        val settings = readerPreferences.settings.value
        val mode = when (settings.ttsVoiceSource) {
            TtsVoiceSource.OFFLINE -> TtsMode.OFFLINE
            TtsVoiceSource.SYSTEM -> TtsMode.SYSTEM
        }
        val voiceId = when (settings.ttsVoiceSource) {
            TtsVoiceSource.OFFLINE -> settings.selectedOfflineVoiceId
            else -> null
        }
        start(bookId, chapterIndex, mode, voiceId, startSentenceIndex = startSentenceIndex)
    }

    fun seekSentence(index: Int) {
        val s = _session.value
        if (!s.active || s.sentenceCount <= 0) return
        if (!focusHelper.requestFocus()) return
        tts.setVolume(1f)
        ducked = false
        tts.seekToSentence(index)
    }

    fun setSpeechRate(rate: Float) {
        readerPreferences.update { it.copy(ttsSpeed = rate.coerceIn(0.5f, 2.5f)) }
        tts.setSpeechRate(rate)
    }

    fun cycleSpeechRate(): Float {
        val options = floatArrayOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        val current = readerPreferences.settings.value.ttsSpeed
        val idx = options.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
        val next = options[(idx + 1).coerceAtLeast(0) % options.size]
        setSpeechRate(next)
        return next
    }

    fun playPause() {
        val s = _session.value
        if (!s.active) return
        when (s.playbackState) {
            TtsPlaybackState.Speaking -> {
                resumeAfterTransientLoss = false
                tts.pause()
            }
            TtsPlaybackState.Paused -> {
                if (!focusHelper.requestFocus()) return
                tts.setVolume(1f)
                ducked = false
                tts.resume()
            }
            TtsPlaybackState.Idle, TtsPlaybackState.Error -> {
                // Prefer current preferences — session defaults to SYSTEM after stop().
                if (s.bookId.isNotBlank()) {
                    startFromPreferences(s.bookId, s.chapterIndex)
                }
            }
        }
    }

    fun pause() {
        if (!_session.value.active) return
        resumeAfterTransientLoss = false
        tts.pause()
    }

    fun stop() {
        loadJob?.cancel()
        resumeAfterTransientLoss = false
        ducked = false
        suppressAutoAdvance = true
        focusHelper.abandonFocus()
        chapters = emptyList()
        voiceId = null
        _session.value = PlaybackSession()
        tts.stop()
        stopService()
        suppressAutoAdvance = false
    }

    fun skipChapter(delta: Int) {
        val s = _session.value
        if (!s.active || chapters.isEmpty()) return
        val next = (s.chapterIndex + delta).coerceIn(0, chapters.lastIndex)
        if (next == s.chapterIndex && delta != 0) return
        updateChapter(
            next,
            autoPlay = s.playbackState == TtsPlaybackState.Speaking ||
                s.playbackState == TtsPlaybackState.Paused
        )
    }

    fun updateChapter(index: Int, autoPlay: Boolean = true) {
        val s = _session.value
        if (!s.active || chapters.isEmpty()) return
        val coerced = index.coerceIn(0, chapters.lastIndex)
        val chapter = chapters[coerced]
        _session.update {
            it.copy(
                chapterIndex = coerced,
                chapterTitle = chapter.title,
                sentenceIndex = 0,
                sentenceCount = 0,
                spokenStart = 0,
                spokenEnd = 0
            )
        }
        persistProgress(s.bookId, coerced)
        if (autoPlay) {
            if (!focusHelper.requestFocus()) return
            tts.setVolume(1f)
            ducked = false
            tts.play(chapter.plainText)
        } else {
            tts.stop()
            _session.update { it.copy(playbackState = TtsPlaybackState.Idle) }
        }
    }

    fun clearMessage() {
        tts.clearMessage()
        _session.update { it.copy(message = null) }
    }

    private fun maybeAdvanceChapter() {
        if (suppressAutoAdvance) return
        val s = _session.value
        if (!s.active) return
        if (s.playbackState != TtsPlaybackState.Idle) return
        if (s.chapterIndex >= chapters.lastIndex) return
        updateChapter(s.chapterIndex + 1, autoPlay = true)
    }

    private fun onFocusGained() {
        if (ducked) {
            tts.setVolume(1f)
            ducked = false
        }
        if (resumeAfterTransientLoss) {
            resumeAfterTransientLoss = false
            if (_session.value.active && _session.value.playbackState == TtsPlaybackState.Paused) {
                tts.resume()
            }
        }
    }

    private fun pauseFromFocus() {
        if (_session.value.playbackState == TtsPlaybackState.Speaking) {
            tts.pause()
        }
    }

    private fun onDuck() {
        val mode = _session.value.ttsMode
        if (mode == TtsMode.SYSTEM) {
            resumeAfterTransientLoss =
                _session.value.playbackState == TtsPlaybackState.Speaking
            pauseFromFocus()
        } else {
            ducked = true
            tts.setVolume(0.2f)
        }
    }

    /**
     * Bind a [MediaController] so Media3 posts the media notification / lock-screen controls.
     * Also starts the FGS explicitly for Android 12+ reliability.
     */
    private fun ensureService() {
        if (serviceStarted) {
            if (controllerFuture == null) connectMediaController()
            return
        }
        serviceStarted = true
        val intent = Intent(app, HearTextPlaybackService::class.java)
        ContextCompat.startForegroundService(app, intent)
        connectMediaController()
    }

    private fun connectMediaController() {
        mainHandler.post {
            runCatching {
                controllerFuture?.let { MediaController.releaseFuture(it) }
                val token = SessionToken(
                    app,
                    ComponentName(app, HearTextPlaybackService::class.java)
                )
                val future = MediaController.Builder(app, token).buildAsync()
                controllerFuture = future
                future.addListener(
                    {
                        runCatching {
                            val controller = future.get()
                            if (_session.value.playbackState == TtsPlaybackState.Speaking) {
                                controller.play()
                            }
                        }
                    },
                    MoreExecutors.directExecutor()
                )
            }
        }
    }

    private fun stopService() {
        mainHandler.post {
            controllerFuture?.let { MediaController.releaseFuture(it) }
            controllerFuture = null
        }
        if (!serviceStarted) return
        app.stopService(Intent(app, HearTextPlaybackService::class.java))
        serviceStarted = false
    }

    private fun persistProgress(bookId: String, chapterIndex: Int) {
        scope.launch {
            val book = bookRepository.getBook(bookId) ?: return@launch
            val total = chapters.size.coerceAtLeast(1)
            val percent = ((chapterIndex + 1f) / total * 100f).coerceIn(0f, 100f)
            // Preserve page offset so listen does not reset reading position.
            // Last chapter while listening counts as finished.
            val resolvedPercent =
                if (chapterIndex >= total - 1) 100f else percent
            runCatching {
                bookRepository.updateProgress(
                    bookId = bookId,
                    chapterIndex = chapterIndex,
                    offset = book.lastOffset,
                    progressPercent = resolvedPercent
                )
            }
        }
    }

    private fun voiceLabelFor(mode: TtsMode): String = when (mode) {
        TtsMode.OFFLINE -> "Offline"
        TtsMode.SYSTEM -> "System"
    }
}
