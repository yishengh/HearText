package com.yishenghuang.heartext

import com.yishenghuang.heartext.util.localizedString

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yishenghuang.heartext.data.AnnotationEntity
import com.yishenghuang.heartext.data.BookEntity
import com.yishenghuang.heartext.data.OfflineVoiceRepository
import com.yishenghuang.heartext.network.ApiCatalogBook
import com.yishenghuang.heartext.network.ApiCatalogCategory
import com.yishenghuang.heartext.network.ApiCatalogChapter
import com.yishenghuang.heartext.network.ApiCatalogPreview
import com.yishenghuang.heartext.network.ApiCatalogTocItem
import com.yishenghuang.heartext.network.ApiOfflineVoice
import com.yishenghuang.heartext.network.ApiUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StoreViewModel(
    private val app: HearTextApp
) : ViewModel() {
    private val catalog = app.container.catalogRepository

    private val _featured = MutableStateFlow<List<ApiCatalogBook>>(emptyList())
    val featured: StateFlow<List<ApiCatalogBook>> = _featured.asStateFlow()

    private val _rankings = MutableStateFlow<List<ApiCatalogBook>>(emptyList())
    val rankings: StateFlow<List<ApiCatalogBook>> = _rankings.asStateFlow()

    private val _categories = MutableStateFlow<List<ApiCatalogCategory>>(emptyList())
    val categories: StateFlow<List<ApiCatalogCategory>> = _categories.asStateFlow()

    private val search = CatalogSearch(viewModelScope,
        fetch = { q, category, page -> catalog.search(q, category, page, PAGE_SIZE) },
        errorMessage = { app.localizedString(R.string.error_store_load) })
    val results = search.state.map { it.items }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val resultsTotal = search.state.map { it.total }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val loadingMore = search.state.map { it.loadingMore }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val hasMore = search.state.map { it.hasMore }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val sectionLoading = MutableStateFlow(false)
    private val sectionError = MutableStateFlow<String?>(null)
    val loading = combine(sectionLoading, search.state) { sections, state -> sections || state.loading }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val error = combine(sectionError, search.state) { sections, state -> state.error ?: sections }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private var refreshJob: kotlinx.coroutines.Job? = null
    private var refreshGeneration = 0L
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory = _selectedCategory.asStateFlow()

    init { refresh() }

    fun refresh() {
        val generation = ++refreshGeneration
        refreshJob?.cancel()
        submitSearch()
        refreshJob = viewModelScope.launch {
            sectionLoading.value = true
            sectionError.value = null
            try {
                val featured = catalog.featured()
                val rankings = catalog.rankings("shelves")
                val categories = catalog.categories()
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                _featured.value = featured
                _rankings.value = rankings
                _categories.value = categories
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                sectionError.value = app.localizedString(R.string.error_store_load)
            } finally {
                if (generation == refreshGeneration) sectionLoading.value = false
            }
        }
    }

    fun setQuery(value: String) { _query.value = value }
    fun selectCategory(slug: String?) {
        _selectedCategory.value = slug
        submitSearch()
    }
    fun submitSearch() {
        sectionError.value = null
        search.submit(_query.value, _selectedCategory.value)
    }
    fun loadMore() = search.loadMore()

    companion object {
        private const val PAGE_SIZE = 50

        fun factory(app: HearTextApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { StoreViewModel(app) }
        }
    }
}

class CatalogDetailViewModel(
    private val app: HearTextApp,
    private val catalogId: String
) : ViewModel() {
    private val catalog = app.container.catalogRepository
    private var downloadJob: kotlinx.coroutines.Job? = null

    private val _book = MutableStateFlow<ApiCatalogBook?>(null)
    val book: StateFlow<ApiCatalogBook?> = _book.asStateFlow()

    private val _toc = MutableStateFlow<List<ApiCatalogTocItem>>(emptyList())
    val toc: StateFlow<List<ApiCatalogTocItem>> = _toc.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _shelvedBook = MutableStateFlow<BookEntity?>(null)
    val shelvedBook: StateFlow<BookEntity?> = _shelvedBook.asStateFlow()

    init {
        viewModelScope.launch {
            _loading.value = true
            runCatching {
                _book.value = catalog.detail(catalogId)
                _toc.value = catalog.toc(catalogId).items
            }.onFailure { _error.value = it.message }
            _loading.value = false
        }
    }

    fun downloadAndShelf() {
        val current = _book.value ?: return
        if (_busy.value) return
        _busy.value = true
        downloadJob = viewModelScope.launch {
            _error.value = null
            try {
                _shelvedBook.value = catalog.downloadAndShelf(current)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _error.value = app.localizedString(R.string.toast_download_failed)
            } finally {
                _busy.value = false
            }
        }
    }

    fun cancelDownload() { downloadJob?.cancel() }

    /** Consume one-shot open-after-download event so back won't re-open. */
    fun consumeShelvedBook() {
        _shelvedBook.value = null
    }

    companion object {
        fun factory(app: HearTextApp, catalogId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { CatalogDetailViewModel(app, catalogId) }
        }
    }
}

class CatalogPreviewViewModel(
    private val app: HearTextApp,
    private val catalogId: String
) : ViewModel() {
    private val catalog = app.container.catalogRepository

    private val _preview = MutableStateFlow<ApiCatalogPreview?>(null)
    val preview: StateFlow<ApiCatalogPreview?> = _preview.asStateFlow()

    private val _chapterIndex = MutableStateFlow(0)
    val chapterIndex: StateFlow<Int> = _chapterIndex.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val currentChapter: ApiCatalogChapter?
        get() = _preview.value?.chapters?.getOrNull(_chapterIndex.value)

    init {
        viewModelScope.launch {
            runCatching {
                _preview.value = catalog.preview(catalogId)
            }.onFailure { _error.value = it.message }
            _loading.value = false
        }
    }

    fun selectChapter(index: Int) {
        val size = _preview.value?.chapters?.size ?: return
        _chapterIndex.value = index.coerceIn(0, (size - 1).coerceAtLeast(0))
    }

    companion object {
        fun factory(app: HearTextApp, catalogId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { CatalogPreviewViewModel(app, catalogId) }
        }
    }
}

class ProfileViewModel(
    private val app: HearTextApp
) : ViewModel() {
    private val api = app.container.api
    private val offline = app.container.offlineVoiceRepository
    private val books = app.container.bookRepository
    private val readingStats = app.container.readingStats

    private val _user = MutableStateFlow<ApiUser?>(null)
    val user: StateFlow<ApiUser?> = _user.asStateFlow()

    private val _offlineVoices = MutableStateFlow<List<ApiOfflineVoice>>(emptyList())
    val offlineVoices: StateFlow<List<ApiOfflineVoice>> = _offlineVoices.asStateFlow()

    private val _installedIds = MutableStateFlow<Set<String>>(emptySet())
    val installedIds: StateFlow<Set<String>> = _installedIds.asStateFlow()

    private val _downloadProgress = MutableStateFlow<OfflineDownloadProgress?>(null)
    val downloadProgress: StateFlow<OfflineDownloadProgress?> = _downloadProgress.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val settings = app.container.readerPreferences.settings

    val libraryStats: StateFlow<com.yishenghuang.heartext.data.LibraryStats> =
        combine(books.observeBooks(), readingStats.totalReadingMs) { list, readingMs ->
            com.yishenghuang.heartext.data.LibraryStats(
                totalBooks = list.size,
                readingBooks = list.count { it.progressPercent > 0f && it.progressPercent < 99.5f },
                finishedBooks = list.count { it.progressPercent >= 99.5f },
                storeBooks = list.count { it.source == com.yishenghuang.heartext.data.BookSource.CATALOG },
                totalReadingMs = readingMs
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            com.yishenghuang.heartext.data.LibraryStats()
        )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching {
                if (app.container.authTokenProvider.isSignedIn && api.isConfigured) {
                    _user.value = api.me()
                    _offlineVoices.value = offline.featured()
                }
            }.onFailure { _message.value = it.message }
            refreshInstalled()
        }
    }

    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            runCatching {
                _user.value = api.updateMe(displayName = name.trim())
                _message.value = app.localizedString(R.string.toast_profile_updated)
            }.onFailure { _message.value = it.message }
        }
    }

    fun downloadOfflineVoice(voice: ApiOfflineVoice) {
        if (_busy.value) return
        _busy.value = true
        voiceDownloadJob = viewModelScope.launch {
            _downloadProgress.value = OfflineDownloadProgress(
                voiceId = voice.id,
                bytesRead = 0L,
                contentLength = voice.fileSizeBytes.coerceAtLeast(0L),
                phase = OfflineDownloadPhase.Downloading
            )
            try {
                offline.downloadAndInstall(voice) { read, total ->
                    val length = if (total > 0) total else voice.fileSizeBytes
                    _downloadProgress.value = OfflineDownloadProgress(
                        voiceId = voice.id,
                        bytesRead = read,
                        contentLength = length,
                        phase = if (length > 0 && read >= length) OfflineDownloadPhase.Installing else OfflineDownloadPhase.Downloading
                    )
                }
                refreshInstalled()
                _message.value = app.localizedString(R.string.toast_voice_installed, voice.name)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _message.value = app.localizedString(R.string.toast_download_failed)
            } finally {
                _downloadProgress.value = null
                _busy.value = false
            }
        }
    }

    private var voiceDownloadJob: kotlinx.coroutines.Job? = null
    fun cancelVoiceDownload() { voiceDownloadJob?.cancel() }

    fun selectOfflineVoice(voice: ApiOfflineVoice) {
        viewModelScope.launch {
            refreshInstalled()
            if (!isInstalled(voice.id)) {
                _message.value = app.localizedString(R.string.toast_need_full_offline)
                return@launch
            }
            app.container.readerPreferences.update {
                it.copy(
                    ttsVoiceSource = com.yishenghuang.heartext.data.TtsVoiceSource.OFFLINE,
                    selectedOfflineVoiceId = voice.id
                )
            }
            _message.value = app.localizedString(R.string.toast_selected_offline, voice.name)
        }
    }

    fun selectSystemVoice() {
        app.container.readerPreferences.update {
            it.copy(
                ttsVoiceSource = com.yishenghuang.heartext.data.TtsVoiceSource.SYSTEM,
                selectedOfflineVoiceId = null
            )
        }
        _message.value = app.localizedString(R.string.toast_selected_system)
    }

    private val _samplePlayingId = MutableStateFlow<String?>(null)
    val samplePlayingId: StateFlow<String?> = _samplePlayingId.asStateFlow()
    private var sampleJob: kotlinx.coroutines.Job? = null

    fun playSample(voiceId: String) {
        if (_samplePlayingId.value == voiceId) {
            stopSample()
            return
        }
        stopSample()
        _samplePlayingId.value = voiceId
        sampleJob = viewModelScope.launch {
            try {
                offline.playSample(voiceId) {
                    _samplePlayingId.value = null
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _samplePlayingId.value = null
                _message.value = app.localizedString(R.string.tts_playback_failed)
            }
        }
    }

    fun stopSample() {
        sampleJob?.cancel()
        sampleJob = null
        offline.stopSample()
        _samplePlayingId.value = null
    }

    override fun onCleared() {
        stopSample()
        super.onCleared()
    }

    fun isInstalled(voiceId: String) = voiceId in _installedIds.value

    private suspend fun refreshInstalled() {
        _installedIds.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { offline.installedIds() }
    }

    private var deletingAccount = false

    fun deleteAccount(onDeleted: () -> Unit) {
        if (deletingAccount) return
        deletingAccount = true
        viewModelScope.launch {
            try {
                api.deleteMe()
                _user.value = null
                _message.value = app.localizedString(R.string.toast_account_deleted)
                onDeleted()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _message.value = app.localizedString(R.string.error_account_delete)
            } finally {
                deletingAccount = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        fun factory(app: HearTextApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProfileViewModel(app) }
        }
    }
}

enum class OfflineDownloadPhase { Downloading, Installing }

data class OfflineDownloadProgress(
    val voiceId: String,
    val bytesRead: Long,
    val contentLength: Long,
    val phase: OfflineDownloadPhase
) {
    val fraction: Float
        get() = when {
            contentLength > 0L -> (bytesRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
            phase == OfflineDownloadPhase.Installing -> 1f
            else -> 0f
        }
}

class AnnotationsViewModel(
    private val app: HearTextApp,
    private val bookId: String
) : ViewModel() {
    private val repo = app.container.annotationRepository
    private val books = app.container.bookRepository

    val annotations = repo.observe(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val book = books.getBook(bookId)
            repo.syncFromServer(bookId, book?.remoteBookId)
        }
    }

    fun addBookmark(chapterIndex: Int) {
        viewModelScope.launch {
            val book = books.getBook(bookId)
            repo.addBookmark(bookId, book?.remoteBookId, chapterIndex)
        }
    }

    fun addNote(chapterIndex: Int, note: String) {
        viewModelScope.launch {
            val book = books.getBook(bookId)
            repo.addNote(bookId, book?.remoteBookId, chapterIndex, note)
        }
    }

    fun delete(entity: AnnotationEntity) {
        viewModelScope.launch { repo.delete(entity) }
    }

    companion object {
        fun factory(app: HearTextApp, bookId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { AnnotationsViewModel(app, bookId) }
        }
    }
}
