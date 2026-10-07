package com.yishenghuang.heartext.tts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger
import com.yishenghuang.heartext.util.CrashReporting
enum class TtsMode {
    SYSTEM,
    OFFLINE
}

data class TtsSentence(
    val text: String,
    /** Inclusive start index in the source chapter text. */
    val start: Int,
    /** Exclusive end index in the source chapter text. */
    val end: Int
)

class TtsController(
    private val app: android.app.Application,
    private val scope: CoroutineScope,
    private val systemEngine: TtsEngine,
    private val offlineEngine: TtsEngine
) {
    private val _state = MutableStateFlow(TtsPlaybackState.Idle)
    val state: StateFlow<TtsPlaybackState> = _state.asStateFlow()
    private val _completions = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    internal val completions = _completions.asSharedFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _sentenceIndex = MutableStateFlow(0)
    val sentenceIndex: StateFlow<Int> = _sentenceIndex.asStateFlow()

    private val _sentenceCount = MutableStateFlow(0)
    val sentenceCount: StateFlow<Int> = _sentenceCount.asStateFlow()

    private val _spokenStart = MutableStateFlow(0)
    val spokenStart: StateFlow<Int> = _spokenStart.asStateFlow()

    private val _spokenEnd = MutableStateFlow(0)
    val spokenEnd: StateFlow<Int> = _spokenEnd.asStateFlow()

    private var job: Job? = null
    private var mode: TtsMode = TtsMode.SYSTEM
    @Volatile private var activeEngine: TtsEngine = systemEngine
    private var voiceId: String? = null
    private var sourceText: String = ""
    private var sentences: List<TtsSentence> = emptyList()
    private var currentIndex: Int = 0
    private var volume: Float = 1f
    private var speechRate: Float = 1f
    /** Bumped on every play/stop so cancelled jobs never start another engine. */
    private val playGeneration = AtomicInteger(0)

    fun configure(mode: TtsMode, voiceId: String?) {
        this.mode = mode
        this.voiceId = voiceId
    }

    fun currentMode(): TtsMode = mode

    fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        systemEngine.setVolume(this.volume)
        offlineEngine.setVolume(this.volume)
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 2.5f)
        systemEngine.setSpeechRate(speechRate)
        offlineEngine.setSpeechRate(speechRate)
    }

    fun currentSpeechRate(): Float = speechRate

    fun play(text: String, startSentenceIndex: Int = 0) {
        val generation = playGeneration.incrementAndGet()
        job?.cancel()
        stopEnginesOnly()
        activeEngine = selectEngine()
        sourceText = text
        sentences = splitSentencesWithRanges(text)
        if (sentences.isEmpty()) {
            _state.value = TtsPlaybackState.Idle
            _sentenceIndex.value = 0
            _sentenceCount.value = 0
            _spokenStart.value = 0
            _spokenEnd.value = 0
            return
        }
        when (mode) {
            TtsMode.OFFLINE -> {
                if (voiceId.isNullOrBlank()) {
                    _state.value = TtsPlaybackState.Error
                    _message.value = app.getString(com.yishenghuang.heartext.R.string.toast_pick_offline_voice)
                    return
                }
            }
            TtsMode.SYSTEM -> Unit
        }
        currentIndex = startSentenceIndex.coerceIn(0, sentences.lastIndex)
        _sentenceCount.value = sentences.size
        publishSentence(currentIndex)
        setVolume(volume)
        setSpeechRate(speechRate)

        job = scope.launch {
            _state.value = TtsPlaybackState.Speaking
            try {
                while (currentIndex < sentences.size && isActive && isCurrentGeneration(generation)) {
                    while (_state.value == TtsPlaybackState.Paused && isActive &&
                        isCurrentGeneration(generation)
                    ) {
                        delay(80)
                    }
                    if (!isActive || !isCurrentGeneration(generation)) break
                    if (_state.value != TtsPlaybackState.Speaking) {
                        _state.value = TtsPlaybackState.Speaking
                    }
                    val batchEnd = packBatchEnd(currentIndex)
                    val batchText = buildBatchText(currentIndex, batchEnd)
                    publishSentence(currentIndex)
                    val engine = activeEngine
                    try {
                        speakBatch(engine, currentIndex, batchEnd, batchText)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Seek/stop cancels the job; never start system TTS as a side effect.
                        if (!isActive || !isCurrentGeneration(generation)) break
                        if (engine === offlineEngine) {
                            CrashReporting.record(
                                e,
                                mapOf(
                                    "tts_engine" to "offline",
                                    "tts_voice_id" to (voiceId ?: ""),
                                    "tts_fallback" to "system"
                                )
                            )
                            _message.value = app.getString(com.yishenghuang.heartext.R.string.tts_system_fallback)
                            stopEnginesOnly()
                            activeEngine = systemEngine
                            if (!isActive || !isCurrentGeneration(generation)) break
                            speakBatch(systemEngine, currentIndex, batchEnd, batchText)
                        } else {
                            throw e
                        }
                    }
                    if (!isActive || !isCurrentGeneration(generation)) break
                    if (_state.value == TtsPlaybackState.Paused) {
                        continue
                    }
                    currentIndex = batchEnd
                }
                if (isActive && isCurrentGeneration(generation) &&
                    _state.value != TtsPlaybackState.Paused
                ) {
                    _state.value = TtsPlaybackState.Idle
                    if (currentIndex >= sentences.size) _completions.emit(generation)
                }
            } catch (e: CancellationException) {
                // Expected on seek/stop — do not surface as error or start another engine.
            } catch (e: Exception) {
                if (isCurrentGeneration(generation)) {
                    CrashReporting.record(
                        e,
                        mapOf("tts_engine" to mode.name.lowercase(), "tts_voice_id" to (voiceId ?: ""))
                    )
                    _state.value = TtsPlaybackState.Error
                    _message.value = app.getString(com.yishenghuang.heartext.R.string.tts_playback_failed)
                }
            }
        }
    }

    internal fun isCurrentGeneration(generation: Int): Boolean =
        playGeneration.get() == generation

    /**
     * Pack consecutive sentences into one TTS request.
     * Offline: short batches (native TTS safety). System: larger batches are fine.
     */
    private fun packBatchEnd(from: Int): Int {
        val maxChars = when (mode) {
            TtsMode.OFFLINE -> OFFLINE_BATCH_MAX_CHARS
            TtsMode.SYSTEM -> SYSTEM_BATCH_MAX_CHARS
        }
        val maxSentences = when (mode) {
            TtsMode.OFFLINE -> OFFLINE_BATCH_MAX_SENTENCES
            TtsMode.SYSTEM -> SYSTEM_BATCH_MAX_SENTENCES
        }
        var end = from
        var chars = 0
        while (end < sentences.size && (end - from) < maxSentences) {
            val len = sentences[end].text.length
            if (end > from && chars + len > maxChars) break
            chars += len
            end++
            if (chars >= maxChars) break
        }
        return end.coerceAtLeast(from + 1).coerceAtMost(sentences.size)
    }

    private fun buildBatchText(from: Int, endExclusive: Int): String {
        return sentences.subList(from, endExclusive).joinToString("") { it.text }
    }

    /**
     * Speak one packed batch; while audio plays, advance sentence highlight by char-weight estimate.
     */
    private suspend fun speakBatch(
        engine: TtsEngine,
        from: Int,
        endExclusive: Int,
        batchText: String
    ) = coroutineScope {
        val batch = sentences.subList(from, endExclusive)
        val weights = batch.map { it.text.length.coerceAtLeast(1) }
        val totalWeight = weights.sum().toFloat().coerceAtLeast(1f)
        val estimatedMs = (totalWeight * MS_PER_CHAR / speechRate.coerceAtLeast(0.5f))
            .toLong()
            .coerceIn(MIN_BATCH_ESTIMATE_MS, MAX_BATCH_ESTIMATE_MS)

        val highlightJob = launch {
            val startMs = android.os.SystemClock.elapsedRealtime()
            while (isActive && _state.value != TtsPlaybackState.Idle) {
                if (_state.value == TtsPlaybackState.Paused) {
                    delay(80)
                    continue
                }
                if (!engine.isSpeaking() &&
                    android.os.SystemClock.elapsedRealtime() - startMs > 400
                ) {
                    break
                }
                val elapsed = android.os.SystemClock.elapsedRealtime() - startMs
                val ratio = (elapsed.toFloat() / estimatedMs).coerceIn(0f, 0.99f)
                var acc = 0f
                var idx = from
                for (i in batch.indices) {
                    acc += weights[i] / totalWeight
                    idx = from + i
                    if (ratio < acc) break
                }
                publishSentence(idx)
                delay(120)
            }
        }
        try {
            engine.speak(batchText, if (engine === systemEngine) null else voiceId)
            awaitEngineOrPause(engine)
            if (_state.value != TtsPlaybackState.Paused) {
                publishSentence((endExclusive - 1).coerceAtLeast(from))
            }
        } finally {
            highlightJob.cancel()
        }
    }

    /** Jump to a sentence within the current chapter text without reloading chapters. */
    fun seekToSentence(index: Int) {
        if (sentences.isEmpty() || sourceText.isBlank()) return
        val target = index.coerceIn(0, sentences.lastIndex)
        play(sourceText, target)
    }

    private fun publishSentence(index: Int) {
        val sentence = sentences.getOrNull(index) ?: return
        _sentenceIndex.value = index
        _spokenStart.value = sentence.start
        _spokenEnd.value = sentence.end
    }

    private suspend fun awaitEngineOrPause(engine: TtsEngine) {
        while (currentCoroutineContext().isActive) {
            if (_state.value == TtsPlaybackState.Paused) {
                while (_state.value == TtsPlaybackState.Paused &&
                    currentCoroutineContext().isActive
                ) {
                    kotlinx.coroutines.delay(80)
                }
                if (!currentCoroutineContext().isActive) return
                continue
            }
            if (!engine.isSpeaking()) return
            kotlinx.coroutines.delay(120)
        }
    }

    fun pause() {
        if (_state.value != TtsPlaybackState.Speaking) return
        activeEngine.pause()
        _state.value = TtsPlaybackState.Paused
    }

    fun resume() {
        if (_state.value != TtsPlaybackState.Paused) return
        _state.value = TtsPlaybackState.Speaking
        try {
            activeEngine.resume()
        } catch (failure: Exception) {
            job?.cancel()
            stopEnginesOnly()
            _state.value = TtsPlaybackState.Error
            _message.value = app.getString(com.yishenghuang.heartext.R.string.tts_playback_failed)
            return
        }
        if (job?.isActive != true && sentences.isNotEmpty() && sourceText.isNotBlank()) {
            play(sourceText, currentIndex)
        }
    }

    /** @deprecated Prefer [resume] without re-supplying text when sentence index is tracked. */
    fun resume(text: String) {
        if (_state.value == TtsPlaybackState.Paused) {
            if (sentences.isEmpty()) {
                play(text)
            } else {
                resume()
            }
        }
    }

    fun stop() {
        playGeneration.incrementAndGet()
        job?.cancel()
        job = null
        stopEnginesOnly()
        sourceText = ""
        sentences = emptyList()
        currentIndex = 0
        _sentenceIndex.value = 0
        _sentenceCount.value = 0
        _spokenStart.value = 0
        _spokenEnd.value = 0
        _state.value = TtsPlaybackState.Idle
    }

    private fun stopEnginesOnly() {
        systemEngine.stop()
        offlineEngine.stop()
    }

    fun clearMessage() {
        _message.value = null
    }

    fun shutdown() {
        stop()
        systemEngine.shutdown()
        offlineEngine.shutdown()
    }

    private fun selectEngine(): TtsEngine = when (mode) {
        TtsMode.OFFLINE -> offlineEngine
        TtsMode.SYSTEM -> systemEngine
    }

    companion object {
        /** Offline neural TTS: one short sentence per generate() to avoid OOM/native issues. */
        private const val OFFLINE_BATCH_MAX_CHARS = 120
        private const val OFFLINE_BATCH_MAX_SENTENCES = 1
        private const val SYSTEM_BATCH_MAX_CHARS = 800
        private const val SYSTEM_BATCH_MAX_SENTENCES = 10
        /** Rough speech rate for highlight estimation (~12.5 chars/sec). */
        private const val MS_PER_CHAR = 80f
        private const val MIN_BATCH_ESTIMATE_MS = 1_500L
        private const val MAX_BATCH_ESTIMATE_MS = 60_000L

        fun splitSentencesWithRanges(text: String): List<TtsSentence> {
            if (text.isBlank()) return emptyList()
            val result = ArrayList<TtsSentence>()
            var start = 0
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '.' || c == '!' || c == '?' || c == '。' || c == '！' || c == '？') {
                    val end = i + 1
                    val raw = text.substring(start, end)
                    val trimmed = raw.trim()
                    if (trimmed.isNotBlank()) {
                        val local = raw.indexOf(trimmed)
                        val absStart = start + local
                        result += TtsSentence(trimmed, absStart, absStart + trimmed.length)
                    }
                    i = end
                    while (i < text.length && text[i].isWhitespace()) i++
                    start = i
                    continue
                }
                i++
            }
            if (start < text.length) {
                val raw = text.substring(start)
                val trimmed = raw.trim()
                if (trimmed.isNotBlank()) {
                    val local = raw.indexOf(trimmed)
                    val absStart = start + local
                    result += TtsSentence(trimmed, absStart, absStart + trimmed.length)
                }
            }
            if (result.isNotEmpty()) return result.flatMap { sentence ->
                // Bound every engine request without dropping text or splitting surrogate pairs.
                val chunks = ArrayList<TtsSentence>()
                var offset = sentence.start
                while (offset < sentence.end) {
                    var end = minOf(offset + OFFLINE_BATCH_MAX_CHARS, sentence.end)
                    if (end < sentence.end && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
                    if (end < sentence.end) {
                        val space = text.lastIndexOf(' ', end - 1)
                        if (space > offset + OFFLINE_BATCH_MAX_CHARS / 2) end = space + 1
                    }
                    chunks += TtsSentence(text.substring(offset, end), offset, end)
                    offset = end
                }
                chunks
            }
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return emptyList()
            val s = text.indexOf(trimmed)
            return listOf(TtsSentence(trimmed, s, s + trimmed.length))
        }

        fun sentenceIndexForOffset(text: String, charOffset: Int): Int {
            val sentences = splitSentencesWithRanges(text)
            if (sentences.isEmpty()) return 0
            val offset = charOffset.coerceAtLeast(0)
            val idx = sentences.indexOfFirst { offset < it.end }
            return if (idx >= 0) idx else sentences.lastIndex
        }
    }
}
