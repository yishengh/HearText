package com.yishenghuang.heartext

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yishenghuang.heartext.data.BookEntity
import com.yishenghuang.heartext.data.BookFormat
import com.yishenghuang.heartext.data.BookRepository
import com.yishenghuang.heartext.data.CatalogRepository
import com.yishenghuang.heartext.data.EpubChapter
import com.yishenghuang.heartext.data.ReaderPreferences
import com.yishenghuang.heartext.data.ReaderSettings
import com.yishenghuang.heartext.data.ReaderThemeMode
import com.yishenghuang.heartext.data.TtsVoiceSource
import com.yishenghuang.heartext.tts.PlaybackCoordinator
import com.yishenghuang.heartext.tts.PlaybackSession
import com.yishenghuang.heartext.tts.TtsPlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(
    private val bookRepository: BookRepository,
    private val app: Application
) : ViewModel() {
    val books = bookRepository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { bookRepository.ensureSampleBooks() }
                .onFailure { _error.value = it.message }
        }
    }

    fun importBook(uri: android.net.Uri, onDone: (BookEntity) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val book = bookRepository.importFromUri(uri)
                onDone(book)
                // Local import completes before optional cloud synchronization.
                bookRepository.syncAfterImport(book)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _error.value = app.getString(R.string.error_import_failed)
            }
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            runCatching { bookRepository.deleteBook(bookId) }
                .onFailure {
                    _error.value = it.message ?: app.getString(R.string.error_delete_failed)
                }
        }
    }

    fun clearError() {
        _error.value = null
    }

    companion object {
        fun factory(app: HearTextApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(app.container.bookRepository, app) }
        }
    }
}

class HomeViewModel(
    private val bookRepository: BookRepository
) : ViewModel() {
    val books = bookRepository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            runCatching { bookRepository.ensureSampleBooks() }
        }
    }

    companion object {
        fun factory(app: HearTextApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(app.container.bookRepository) }
        }
    }
}

class BookOverviewViewModel(
    private val bookId: String,
    private val bookRepository: BookRepository,
    private val catalogRepository: CatalogRepository? = null
) : ViewModel() {
    val book = bookRepository.observeBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            bookRepository.ensureChapterCount(bookId)
            val current = bookRepository.getBook(bookId) ?: return@launch
            if (!current.description.isNullOrBlank()) return@launch
            val catalogId = current.catalogBookId?.takeIf { it.isNotBlank() } ?: return@launch
            val repo = catalogRepository ?: return@launch
            runCatching {
                val detail = repo.detail(catalogId)
                bookRepository.updateDescription(bookId, detail.description)
            }
        }
    }

    fun changeCover(uri: android.net.Uri) {
        viewModelScope.launch {
            bookRepository.setUserCover(bookId, uri)
        }
    }

    companion object {
        fun factory(app: HearTextApp, bookId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BookOverviewViewModel(
                    bookId,
                    app.container.bookRepository,
                    app.container.catalogRepository
                )
            }
        }
    }
}

class ReaderViewModel(
    private val bookId: String,
    private val bookRepository: BookRepository,
    private val readerPreferences: ReaderPreferences,
    private val playbackCoordinator: PlaybackCoordinator,
    private val readerSessions: com.yishenghuang.heartext.readium.ReaderSessionRepository,
    private val fontStore: com.yishenghuang.heartext.data.FontStore,
    private val annotationRepository: com.yishenghuang.heartext.data.AnnotationRepository,
    private val app: Application,
    private val persistenceScope: kotlinx.coroutines.CoroutineScope
) : ViewModel() {
    private var openedSession: com.yishenghuang.heartext.readium.ReaderSession? = null
    private var lastKnownOffset = 0
    val book = bookRepository.observeBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<ReaderSettings> = readerPreferences.settings

    val playbackSession: StateFlow<PlaybackSession> = playbackCoordinator.session

    val ttsState: StateFlow<TtsPlaybackState> = playbackCoordinator.session
        .map { session ->
            if (session.active && session.bookId == bookId) session.playbackState
            else TtsPlaybackState.Idle
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TtsPlaybackState.Idle)

    val ttsMessage: StateFlow<String?> = playbackCoordinator.session
        .map { session ->
            if (session.active && session.bookId == bookId) session.message else null
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val annotations = annotationRepository.observe(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _chapters = MutableStateFlow<List<EpubChapter>>(emptyList())
    val chapters: StateFlow<List<EpubChapter>> = _chapters.asStateFlow()

    private val _chapterIndex = MutableStateFlow(0)
    val chapterIndex: StateFlow<Int> = _chapterIndex.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    private val _showToc = MutableStateFlow(false)
    val showToc: StateFlow<Boolean> = _showToc.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _useReadium = MutableStateFlow(false)
    val useReadium: StateFlow<Boolean> = _useReadium.asStateFlow()

    private val _listeningEnabled = MutableStateFlow(true)
    val listeningEnabled: StateFlow<Boolean> = _listeningEnabled.asStateFlow()

    internal var textPosition: com.yishenghuang.heartext.data.TextPosition? = null
        private set

    private val _sessionReady = MutableStateFlow(false)
    val sessionReady: StateFlow<Boolean> = _sessionReady.asStateFlow()

    init {
        viewModelScope.launch {
            val entity = bookRepository.getBook(bookId)
            if (entity == null) {
                _error.value = app.getString(R.string.error_book_not_found)
                _loading.value = false
                return@launch
            }
            lastKnownOffset = entity.lastOffset
            textPosition = com.yishenghuang.heartext.data.TextPosition.decode(entity.locatorJson)
                ?.takeIf { it.chapter == entity.lastChapterIndex }

            when (entity.format) {
                BookFormat.TXT, BookFormat.EPUB -> {
                    // Lumi ReadView engine for all text books (including catalog EPUB).
                    _useReadium.value = false
                    _listeningEnabled.value = true
                    runCatching { bookRepository.loadChapterTexts(entity) }
                        .onSuccess { loaded ->
                            _chapters.value = loaded
                            _chapterIndex.value =
                                entity.lastChapterIndex.coerceIn(0, (loaded.size - 1).coerceAtLeast(0))
                            _sessionReady.value = true
                        }
                        .onFailure {
                            _error.value = it.message ?: app.getString(R.string.error_load_chapters)
                        }
                    _loading.value = false
                }
                BookFormat.PDF -> {
                    _useReadium.value = true
                    _listeningEnabled.value = false
                    readerSessions.open(bookId, entity.filePath, entity.locatorJson)
                        .onSuccess {
                            openedSession = it
                            _sessionReady.value = true
                            _loading.value = false
                        }
                        .onFailure {
                            _error.value = it.message ?: app.getString(R.string.error_open_readium)
                            _loading.value = false
                        }
                }
            }
            runCatching {
                annotationRepository.syncFromServer(bookId, entity.remoteBookId)
            }
        }

        viewModelScope.launch {
            playbackCoordinator.session.collect { session ->
                if (session.active && session.bookId == bookId &&
                    session.chapterIndex != _chapterIndex.value &&
                    _chapters.value.isNotEmpty()
                ) {
                    _chapterIndex.value = session.chapterIndex.coerceIn(
                        0,
                        _chapters.value.lastIndex
                    )
                }
            }
        }
    }

    fun addBookmark(pageIndex: Int = 0) {
        viewModelScope.launch {
            val entity = bookRepository.getBook(bookId)
            val chapter = _chapterIndex.value
            val page = pageIndex.coerceAtLeast(0)
            val existing = annotationRepository.findBookmark(bookId, chapter, page)
            if (existing != null) {
                annotationRepository.delete(existing)
                _bookmarkToast.value = app.getString(R.string.toast_bookmark_removed)
            } else {
                annotationRepository.addBookmark(
                    bookId = bookId,
                    remoteBookId = entity?.remoteBookId,
                    chapterIndex = chapter,
                    pageIndex = page
                )
                _bookmarkToast.value = app.getString(R.string.toast_bookmark_added)
            }
        }
    }

    private val _bookmarkToast = MutableStateFlow<String?>(null)
    val bookmarkToast: StateFlow<String?> = _bookmarkToast.asStateFlow()

    fun clearBookmarkToast() {
        _bookmarkToast.value = null
    }

    fun addNote(note: String) {
        viewModelScope.launch {
            val entity = bookRepository.getBook(bookId)
            annotationRepository.addNote(
                bookId = bookId,
                remoteBookId = entity?.remoteBookId,
                chapterIndex = _chapterIndex.value,
                note = note
            )
        }
    }

    fun currentText(): String = _chapters.value.getOrNull(_chapterIndex.value)?.plainText.orEmpty()

    fun openSettings(open: Boolean) {
        _showSettings.value = open
    }

    fun openToc(open: Boolean) {
        _showToc.value = open
    }

    private val _showBookmarks = MutableStateFlow(false)
    val showBookmarks: StateFlow<Boolean> = _showBookmarks.asStateFlow()

    fun openBookmarks(open: Boolean) {
        _showBookmarks.value = open
        if (open) {
            viewModelScope.launch {
                annotationRepository.dedupeBookmarks(bookId)
            }
        }
    }

    private val _showSearch = MutableStateFlow(false)
    val showSearch: StateFlow<Boolean> = _showSearch.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<ReaderSearchHit>>(emptyList())
    val searchResults: StateFlow<List<ReaderSearchHit>> = _searchResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private var searchJob: Job? = null

    fun openSearch(open: Boolean) {
        _showSearch.value = open
        if (!open) {
            searchJob?.cancel()
            _searching.value = false
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        runSearch(debounceMs = 220)
    }

    fun runSearch(debounceMs: Long = 0) {
        searchJob?.cancel()
        val q = _searchQuery.value.trim()
        if (q.isEmpty()) {
            _searchResults.value = emptyList()
            _searching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            _searching.value = true
            val hits = withContext(Dispatchers.Default) {
                searchChapters(
                    chapters = _chapters.value,
                    query = q,
                    limit = SEARCH_RESULT_LIMIT,
                    blankChapterTitle = { index ->
                        app.getString(R.string.reader_chapter_n, index + 1)
                    }
                )
            }
            _searchResults.value = hits
            _searching.value = false
        }
    }

    fun selectChapter(index: Int, startAtEnd: Boolean = false) {
        _chapterIndex.value = index.coerceIn(0, (_chapters.value.size - 1).coerceAtLeast(0))
        _showToc.value = false
        if (!startAtEnd) {
            persistProgress(offset = 0)
        }
        val session = playbackCoordinator.session.value
        if (session.active && session.bookId == bookId &&
            (session.playbackState == TtsPlaybackState.Speaking ||
                session.playbackState == TtsPlaybackState.Paused)
        ) {
            playbackCoordinator.updateChapter(_chapterIndex.value, autoPlay = true)
        }
    }

    fun nextChapter() {
        if (_chapterIndex.value < _chapters.value.lastIndex) {
            selectChapter(_chapterIndex.value + 1)
        }
    }

    fun previousChapter(startAtEnd: Boolean = false) {
        if (_chapterIndex.value > 0) {
            selectChapter(_chapterIndex.value - 1, startAtEnd = startAtEnd)
        }
    }

    fun updateFontScale(scale: Float) {
        readerPreferences.update { it.copy(fontScale = scale.coerceIn(0.8f, 2f)) }
    }

    fun updateTheme(mode: ReaderThemeMode) {
        readerPreferences.update { it.copy(themeMode = mode) }
    }

    fun updateChineseMode(mode: String) {
        val resolved = mode.takeIf { it in setOf("original", "simplified", "traditional") } ?: "original"
        readerPreferences.update { it.copy(chineseMode = resolved) }
    }

    fun updateVolumeKeyPageTurn(enabled: Boolean) {
        readerPreferences.update { it.copy(volumeKeyPageTurn = enabled) }
    }

    fun updateBrightness(value: Float) {
        readerPreferences.update { it.copy(brightness = value.coerceIn(0.3f, 1f)) }
    }

    fun selectOfflineVoice(voiceId: String) {
        val id = voiceId.trim()
        if (id.isBlank()) return
        readerPreferences.update {
            it.copy(
                ttsVoiceSource = com.yishenghuang.heartext.data.TtsVoiceSource.OFFLINE,
                selectedOfflineVoiceId = id
            )
        }
    }

    fun selectSystemVoice() {
        readerPreferences.update {
            it.copy(
                ttsVoiceSource = com.yishenghuang.heartext.data.TtsVoiceSource.SYSTEM,
                selectedOfflineVoiceId = null
            )
        }
    }

    fun updatePageTurnEffect(effect: com.yishenghuang.heartext.data.PageTurnEffect) {
        val book = book.value
        val resolved =
            if (effect == com.yishenghuang.heartext.data.PageTurnEffect.CURL && book?.format == BookFormat.PDF) {
                com.yishenghuang.heartext.data.PageTurnEffect.SLIDE
            } else {
                effect
            }
        readerPreferences.update { it.copy(pageTurnEffect = resolved) }
    }

    /** Last known pages in the current chapter (from ReadView). */
    @Volatile
    private var lastKnownChapterPages: Int = 1

    /** Sync chapter/page from the Lumi ReadView engine. */
    fun onEnginePageChanged(chapterIndex: Int, pageInChapter: Int, chapterTotalPages: Int, characterOffset: Int? = null) {
        val chapters = _chapters.value
        if (chapters.isNotEmpty()) {
            _chapterIndex.value = chapterIndex.coerceIn(0, chapters.lastIndex)
        }
        textPosition = characterOffset?.takeIf { it >= 0 }?.let {
            com.yishenghuang.heartext.data.TextPosition(chapterIndex, it)
        }
        lastKnownOffset = pageInChapter.coerceAtLeast(0)
        lastKnownChapterPages = chapterTotalPages.coerceAtLeast(1)
        persistProgress(
            offset = pageInChapter.coerceAtLeast(0),
            chapterPages = lastKnownChapterPages
        )
    }

    fun updateFontFamily(family: com.yishenghuang.heartext.data.ReaderFontFamily) {
        readerPreferences.update { it.copy(fontFamily = family) }
    }

    fun updateLetterSpacing(value: Float) {
        readerPreferences.update { it.copy(letterSpacing = value.coerceIn(-0.05f, 0.2f)) }
    }

    fun updateLineHeight(value: Float) {
        readerPreferences.update { it.copy(lineHeight = value.coerceIn(1.0f, 2.2f)) }
    }

    fun updateParagraphSpacing(value: Float) {
        readerPreferences.update { it.copy(paragraphSpacing = value.coerceIn(0f, 2f)) }
    }

    fun importFont(uri: android.net.Uri) {
        viewModelScope.launch {
            val path = fontStore.importFromUri(uri)
            if (path == null) {
                _error.value = app.getString(R.string.error_import_failed)
                return@launch
            }
            readerPreferences.update {
                it.copy(
                    customFontPath = path,
                    fontFamily = com.yishenghuang.heartext.data.ReaderFontFamily.CUSTOM
                )
            }
        }
    }

    /** Provides current page's chapter-local char offset for mapping to a start sentence. */
    @Volatile
    var pageCharOffsetProvider: (() -> Int?)? = null

    private fun startSentenceIndexForCurrentPage(): Int {
        val chapter = _chapters.value.getOrNull(_chapterIndex.value) ?: return 0
        val offset = pageCharOffsetProvider?.invoke() ?: 0
        return com.yishenghuang.heartext.tts.TtsController.sentenceIndexForOffset(
            chapter.plainText,
            offset
        )
    }

    fun toggleListen() {
        if (!_listeningEnabled.value) return
        viewModelScope.launch {
            val session = playbackCoordinator.session.value
            val sameBook = session.active && session.bookId == bookId
            when {
                sameBook && session.playbackState == TtsPlaybackState.Speaking ->
                    playbackCoordinator.pause()
                sameBook && session.playbackState == TtsPlaybackState.Paused ->
                    playbackCoordinator.playPause()
                else ->
                    listenFromCurrentPage()
            }
        }
    }

    /** Always restart listening from the sentence mapped to the visible page. */
    fun listenFromCurrentPage() {
        if (!_listeningEnabled.value) return
        playbackCoordinator.startFromPreferences(
            bookId = bookId,
            chapterIndex = _chapterIndex.value,
            startSentenceIndex = startSentenceIndexForCurrentPage()
        )
    }

    fun cycleSpeechRate() = playbackCoordinator.cycleSpeechRate()

    fun stopListen() = playbackCoordinator.stop()

    fun clearTtsMessage() = playbackCoordinator.clearMessage()

    /** Open player page without starting playback. */
    fun preparePlayer() {
        val session = playbackCoordinator.session.value
        if (session.active && session.bookId == bookId) {
            if (session.chapterIndex != _chapterIndex.value &&
                (session.playbackState == TtsPlaybackState.Idle ||
                    session.playbackState == TtsPlaybackState.Error)
            ) {
                playbackCoordinator.updateChapter(_chapterIndex.value, autoPlay = false)
            }
        }
        // Do not call startFromPreferences — 听书 only opens the player.
    }

    private fun saveReadingPosition(write: suspend () -> Unit) {
        persistenceScope.launch {
            try { write() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { _error.value = app.getString(R.string.error_save_progress) }
        }
    }

    fun persistProgress(offset: Int? = null, chapterPages: Int? = null) {
        if (_useReadium.value || !_sessionReady.value) return
        val chapters = _chapters.value
        val totalChapters = chapters.size.coerceAtLeast(1)
        val chapter = _chapterIndex.value.coerceIn(0, totalChapters - 1)
        val pages = (chapterPages ?: lastKnownChapterPages).coerceAtLeast(1)
        val page = (offset ?: lastKnownOffset).coerceAtLeast(0)
        if (page != lastKnownOffset || textPosition?.chapter != chapter) textPosition = null
        lastKnownOffset = page
        val locator = (textPosition ?: com.yishenghuang.heartext.data.TextPosition(chapter, -1)).encode()
        val percent = if (chapters.isEmpty()) {
            book.value?.progressPercent ?: 0f
        } else {
            computeReadingProgressPercent(
                chapterIndex = chapter,
                pageIndex = page,
                chapterPageCount = pages,
                totalChapters = totalChapters
            )
        }
        val timestamp = bookRepository.captureProgressTimestamp()
        saveReadingPosition {
            bookRepository.updateProgress(
                bookId = bookId,
                chapterIndex = chapter,
                offset = page,
                progressPercent = percent,
                locatorJson = locator,
                totalChapters = chapters.size.takeIf { it > 0 },
                updatedAt = timestamp
            )
        }
    }

    fun persistLocator(locatorJson: String, progressPercent: Float) {
        if (!_sessionReady.value) return
        val chapter = _chapterIndex.value
        val timestamp = bookRepository.captureProgressTimestamp()
        saveReadingPosition {
            bookRepository.updateProgress(
                bookId = bookId,
                chapterIndex = chapter,
                offset = 0,
                progressPercent = progressPercent,
                locatorJson = locatorJson,
                updatedAt = timestamp
            )
        }
    }

    fun applySearchJump(hit: ReaderSearchHit) {
        _chapterIndex.value = hit.chapterIndex.coerceIn(0, (_chapters.value.size - 1).coerceAtLeast(0))
        _showSearch.value = false
        persistProgress(offset = 0)
    }

    override fun onCleared() {
        // Do not stop PlaybackCoordinator — playback continues in the foreground service.
        val session = openedSession
        persistenceScope.launch {
            if (session != null) readerSessions.close(session)
        }
        super.onCleared()
    }

    companion object {
        const val SEARCH_RESULT_LIMIT = 80

        fun factory(app: HearTextApp, bookId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ReaderViewModel(
                    bookId = bookId,
                    bookRepository = app.container.bookRepository,
                    readerPreferences = app.container.readerPreferences,
                    playbackCoordinator = app.container.playbackCoordinator,
                    readerSessions = app.container.readerSessions,
                    fontStore = app.container.fontStore,
                    annotationRepository = app.container.annotationRepository,
                    app = app,
                    persistenceScope = app.container.applicationScope
                )
            }
        }
    }
}

data class ReaderSearchHit(
    val chapterIndex: Int,
    val chapterTitle: String,
    val startOffset: Int,
    val matchLength: Int,
    val snippet: String
)

private fun searchChapters(
    chapters: List<EpubChapter>,
    query: String,
    limit: Int,
    blankChapterTitle: (Int) -> String
): List<ReaderSearchHit> {
    if (query.isEmpty() || chapters.isEmpty()) return emptyList()
    val hits = ArrayList<ReaderSearchHit>(minOf(limit, 32))
    val needle = query
    val useIgnoreCase = query.any { it in 'A'..'Z' || it in 'a'..'z' }
    for ((chapterIndex, chapter) in chapters.withIndex()) {
        val text = chapter.plainText
        if (text.isEmpty()) continue
        var from = 0
        while (from < text.length && hits.size < limit) {
            val idx = if (useIgnoreCase) {
                text.indexOf(needle, startIndex = from, ignoreCase = true)
            } else {
                text.indexOf(needle, startIndex = from)
            }
            if (idx < 0) break
            hits += ReaderSearchHit(
                chapterIndex = chapterIndex,
                chapterTitle = chapter.title.ifBlank { blankChapterTitle(chapterIndex) },
                startOffset = idx,
                matchLength = needle.length,
                snippet = buildSearchSnippet(text, idx, needle.length)
            )
            from = idx + needle.length.coerceAtLeast(1)
        }
        if (hits.size >= limit) break
    }
    return hits
}

private fun buildSearchSnippet(text: String, start: Int, matchLen: Int, radius: Int = 28): String {
    val from = (start - radius).coerceAtLeast(0)
    val to = (start + matchLen + radius).coerceAtMost(text.length)
    val raw = text.substring(from, to).replace('\n', ' ').trim()
    val prefix = if (from > 0) "…" else ""
    val suffix = if (to < text.length) "…" else ""
    return prefix + raw + suffix
}

class PlayerViewModel(
    private val bookId: String,
    private val bookRepository: BookRepository,
    private val readerPreferences: ReaderPreferences,
    private val offlineVoices: com.yishenghuang.heartext.data.OfflineVoiceRepository,
    private val playbackCoordinator: PlaybackCoordinator,
    private val app: Application
) : ViewModel() {
    val session: StateFlow<PlaybackSession> = playbackCoordinator.session
    val settings: StateFlow<ReaderSettings> = readerPreferences.settings

    private val _book = MutableStateFlow<BookEntity?>(null)
    val book: StateFlow<BookEntity?> = _book.asStateFlow()

    private val _installedOffline = MutableStateFlow(offlineVoices.listInstalled())
    val installedOffline:
        StateFlow<List<com.yishenghuang.heartext.data.InstalledOfflineVoice>> =
        _installedOffline.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    init {
        viewModelScope.launch {
            _book.value = bookRepository.getBook(bookId)
        }
        refreshOffline()
    }

    fun playPause() {
        val s = session.value
        if (s.active && s.bookId == bookId) {
            playbackCoordinator.playPause()
            return
        }
        viewModelScope.launch {
            val chapter = _book.value?.lastChapterIndex?.coerceAtLeast(0)
                ?: bookRepository.getBook(bookId)?.lastChapterIndex?.coerceAtLeast(0)
                ?: 0
            playbackCoordinator.startFromPreferences(bookId, chapter)
        }
    }

    fun cycleSpeechRate(): Float {
        val next = playbackCoordinator.cycleSpeechRate()
        _toast.value = app.getString(R.string.toast_speed, formatSpeed(next))
        return next
    }

    fun stop() = playbackCoordinator.stop()
    fun skipChapter(delta: Int) = playbackCoordinator.skipChapter(delta)
    fun seekSentence(index: Int) = playbackCoordinator.seekSentence(index)
    fun clearToast() {
        _toast.value = null
    }

    fun clearSessionMessage() = playbackCoordinator.clearMessage()

    fun selectSystemVoice() {
        readerPreferences.update {
            it.copy(
                ttsVoiceSource = TtsVoiceSource.SYSTEM,
                selectedOfflineVoiceId = null
            )
        }
        restartWithPreferences(app.getString(R.string.toast_switched_system))
    }

    fun selectOfflineVoice(voiceId: String) {
        val id = voiceId.trim()
        if (id.isBlank()) return
        if (!offlineVoices.isInstalled(id)) {
            _toast.value = app.getString(R.string.toast_need_offline_voice)
            return
        }
        readerPreferences.update {
            it.copy(
                ttsVoiceSource = TtsVoiceSource.OFFLINE,
                selectedOfflineVoiceId = id
            )
        }
        restartWithPreferences(app.getString(R.string.toast_switched_offline))
    }

    private fun restartWithPreferences(toast: String) {
        viewModelScope.launch {
            val chapter = playbackCoordinator.session.value
                .takeIf { it.active && it.bookId == bookId }
                ?.chapterIndex
                ?: bookRepository.getBook(bookId)?.lastChapterIndex?.coerceAtLeast(0)
                ?: 0
            playbackCoordinator.startFromPreferences(bookId, chapter)
            _toast.value = toast
        }
    }

    fun refreshOffline() {
        _installedOffline.value = offlineVoices.listInstalled()
    }

    companion object {
        fun formatSpeed(speed: Float): String {
            val v = if (speed == speed.toInt().toFloat()) {
                "${speed.toInt()}"
            } else {
                String.format(java.util.Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')
            }
            return "${v}x"
        }

        fun factory(app: HearTextApp, bookId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PlayerViewModel(
                    bookId = bookId,
                    bookRepository = app.container.bookRepository,
                    readerPreferences = app.container.readerPreferences,
                    offlineVoices = app.container.offlineVoiceRepository,
                    playbackCoordinator = app.container.playbackCoordinator,
                    app = app
                )
            }
        }
    }
}

fun Application.asHearTextApp(): HearTextApp = this as HearTextApp

/** Reading progress: chapter share + page share; last page of last chapter = 100%. */
internal fun computeReadingProgressPercent(
    chapterIndex: Int,
    pageIndex: Int,
    chapterPageCount: Int,
    totalChapters: Int
): Float {
    val chapters = totalChapters.coerceAtLeast(1)
    val pages = chapterPageCount.coerceAtLeast(1)
    val chapter = chapterIndex.coerceIn(0, chapters - 1)
    val page = pageIndex.coerceIn(0, pages - 1)
    if (chapter >= chapters - 1 && page >= pages - 1) return 100f
    val chapterShare = 100f / chapters
    val pageShare = (page + 1f) / pages
    return (chapter * chapterShare + pageShare * chapterShare).coerceIn(0f, 100f)
}
