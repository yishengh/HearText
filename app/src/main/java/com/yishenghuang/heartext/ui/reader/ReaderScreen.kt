package com.yishenghuang.heartext.ui.reader

import android.view.ViewGroup
import android.widget.FrameLayout
import com.yishenghuang.heartext.data.TextPosition
import com.yishenghuang.heartext.data.bookmarkPositionKey
import com.yishenghuang.heartext.data.matchesBookmarkPage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yishenghuang.heartext.MainActivity
import com.yishenghuang.heartext.R
import com.yishenghuang.heartext.ReaderSearchHit
import com.yishenghuang.heartext.ReaderViewModel
import com.yishenghuang.heartext.computeReadingProgressPercent
import com.yishenghuang.heartext.data.BookFormat
import com.yishenghuang.heartext.data.EpubChapter
import com.yishenghuang.heartext.data.PageTurnEffect
import com.yishenghuang.heartext.data.ReaderFontFamily
import com.yishenghuang.heartext.data.ReaderSettings
import com.yishenghuang.heartext.data.ReaderThemeMode
import com.yishenghuang.heartext.tts.PlaybackSession
import com.yishenghuang.heartext.tts.TtsPlaybackState
import com.yishenghuang.heartext.ui.components.ImmersiveMode
import com.yishenghuang.heartext.ui.reader.engine.ReadView
import com.yishenghuang.heartext.ui.reader.engine.ReadViewCallbacks
import com.yishenghuang.heartext.ui.theme.HearPurple
import com.yishenghuang.heartext.ui.theme.HearReaderDark
import com.yishenghuang.heartext.ui.theme.HearReaderLight
import com.yishenghuang.heartext.ui.theme.HearReaderSepia
import com.yishenghuang.heartext.ui.theme.HearTextSecondary
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onOpenPlayer: (bookId: String) -> Unit = {},
    onLoadingComplete: () -> Unit = {}
) {
    ImmersiveMode()

    val context = LocalContext.current
    val app = context.applicationContext as com.yishenghuang.heartext.HearTextApp
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME ->
                    app.container.readingStats.startSession()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE ->
                    app.container.readingStats.endSession()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            app.container.readingStats.endSession()
        }
    }

    val book by viewModel.book.collectAsStateWithLifecycle()
    val useReadium by viewModel.useReadium.collectAsStateWithLifecycle()
    val sessionReady by viewModel.sessionReady.collectAsStateWithLifecycle()
    val listeningEnabled by viewModel.listeningEnabled.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val chapterIndex by viewModel.chapterIndex.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val showSettings by viewModel.showSettings.collectAsStateWithLifecycle()
    val showToc by viewModel.showToc.collectAsStateWithLifecycle()
    val showBookmarks by viewModel.showBookmarks.collectAsStateWithLifecycle()
    val showSearch by viewModel.showSearch.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val ttsMessage by viewModel.ttsMessage.collectAsStateWithLifecycle()
    val bookmarkToast by viewModel.bookmarkToast.collectAsStateWithLifecycle()
    val bookmarkBusy by viewModel.bookmarkBusy.collectAsStateWithLifecycle()
    val playbackSession by viewModel.playbackSession.collectAsStateWithLifecycle()
    val annotations by viewModel.annotations.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var menuVisible by rememberSaveable { mutableStateOf(false) }
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    var pageCount by rememberSaveable { mutableIntStateOf(1) }
    var readiumPercent by remember { mutableFloatStateOf(book?.progressPercent ?: 0f) }
    var pageSeeded by rememberSaveable { mutableStateOf(false) }
    var pendingSearchJump by remember { mutableStateOf<ReaderSearchHit?>(null) }
    var pendingBookmarkJump by remember { mutableStateOf<TextPosition?>(null) }
    var suppressPageSync by remember { mutableStateOf(false) }
    val isCurrentPageBookmarked = annotations.any {
        it.matchesBookmarkPage(chapterIndex, pageIndex, viewModel.pageCharRangeProvider?.invoke())
    }

    LaunchedEffect(book?.id, loading) {
        val current = book
        if (!loading && current != null && !pageSeeded) {
            pageIndex = current.lastOffset.coerceAtLeast(0)
            pageSeeded = true
        }
    }

    var appliedTypography by remember { mutableStateOf(settings) }
    LaunchedEffect(Unit) {
        viewModel.settings
            .map {
                TypographyKey(
                    it.fontScale,
                    it.fontFamily,
                    it.customFontPath,
                    it.letterSpacing,
                    it.lineHeight,
                    it.paragraphSpacing
                ) to it
            }
            .distinctUntilChangedBy { it.first }
            .debounce(120)
            .collect { (_, full) -> appliedTypography = full }
    }

    val curlAvailable = book?.format != BookFormat.PDF
    // TXT/EPUB: Lumi ReadView. PDF: Readium only.
    val usePaginatedText = !loading && chapters.isNotEmpty() && curlAvailable && !useReadium

    LaunchedEffect(loading, sessionReady, error, usePaginatedText, useReadium) {
        if (!loading && error == null && (usePaginatedText || sessionReady || !useReadium)) {
            onLoadingComplete()
        }
    }

    LaunchedEffect(ttsMessage) {
        ttsMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearTtsMessage()
        }
    }

    LaunchedEffect(showSettings, showToc, showSearch, showBookmarks) {
        if (showSettings || showToc || showSearch || showBookmarks) menuVisible = true
    }

    LaunchedEffect(bookmarkToast) {
        bookmarkToast?.let {
            snackbar.showSnackbar(it)
            viewModel.clearBookmarkToast()
        }
    }

    val baseBg = when (settings.themeMode) {
        ReaderThemeMode.LIGHT -> HearReaderLight
        ReaderThemeMode.SEPIA -> HearReaderSepia
        ReaderThemeMode.DARK -> HearReaderDark
    }
    val bg = dimReaderBackground(baseBg, settings.brightness)
    val fg = if (settings.themeMode == ReaderThemeMode.DARK) Color.White else Color(0xFF2C2C2C)
    val panelBg = if (settings.themeMode == ReaderThemeMode.DARK) Color(0xFF2A2930) else Color.White

    val chapterTitle = chapters.getOrNull(chapterIndex)?.title
        ?: book?.title.orEmpty()
    val progressPercent = when {
        usePaginatedText || !useReadium -> {
            computeReadingProgressPercent(
                chapterIndex = chapterIndex,
                pageIndex = pageIndex,
                chapterPageCount = pageCount,
                totalChapters = chapters.size.coerceAtLeast(1)
            )
        }
        else -> readiumPercent
    }
    val pageLabel = when {
        usePaginatedText -> "${pageIndex + 1} / ${pageCount.coerceAtLeast(1)}"
        chapters.isNotEmpty() -> "${chapterIndex + 1} / ${chapters.size}"
        else -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        when {
            loading || (useReadium && !sessionReady && error == null && !usePaginatedText) -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = HearPurple)
                }
            }
            error != null && !usePaginatedText -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    error ?: stringResource(R.string.common_error),
                    color = fg,
                    modifier = Modifier.padding(24.dp)
                )
            }
            else -> {
                ReaderImmersiveChrome(
                    menuVisible = menuVisible,
                    title = book?.title.orEmpty(),
                    chapterTitle = chapterTitle,
                    progressPercent = progressPercent,
                    pageLabel = pageLabel,
                    bg = bg,
                    fg = fg,
                    panelBg = panelBg,
                    listeningEnabled = listeningEnabled,
                    ttsState = ttsState,
                    ttsSourceLabel = when (settings.ttsVoiceSource) {
                        com.yishenghuang.heartext.data.TtsVoiceSource.OFFLINE ->
                            stringResource(R.string.reader_offline_short)
                        else -> stringResource(R.string.reader_system_short)
                    },
                    ttsSpeed = settings.ttsSpeed,
                    onBack = {
                        viewModel.persistProgress(
                            offset = pageIndex,
                            chapterPages = pageCount.coerceAtLeast(1)
                        )
                        onBack()
                    },
                    onToggleListen = { viewModel.toggleListen() },
                    onListenFromPage = { viewModel.listenFromCurrentPage() },
                    onStopListen = { viewModel.stopListen() },
                    onCycleSpeed = { viewModel.cycleSpeechRate() },
                    onOpenPlayer = {
                        val id = book?.id ?: return@ReaderImmersiveChrome
                        viewModel.preparePlayer()
                        onOpenPlayer(id)
                    },
                    onOpenSearch = {
                        menuVisible = true
                        viewModel.openSearch(true)
                    },
                    onAddBookmark = { viewModel.addBookmark(pageIndex) },
                    onOpenBookmarks = { viewModel.openBookmarks(true) },
                    isCurrentPageBookmarked = isCurrentPageBookmarked,
                    bookmarkEnabled = !bookmarkBusy,
                    onOpenToc = { viewModel.openToc(true) },
                    onOpenSettings = { viewModel.openSettings(true) },
                    onToggleMenu = { menuVisible = !menuVisible },
                    showCenterTapOverlay = !usePaginatedText
                ) {
                    when {
                        usePaginatedText -> LumiReadHost(
                            chapters = chapters,
                            chapterIndex = chapterIndex,
                            pageIndex = pageIndex,
                            settings = appliedTypography.copy(
                                themeMode = settings.themeMode,
                                brightness = settings.brightness,
                                pageTurnEffect = settings.pageTurnEffect,
                                chineseMode = settings.chineseMode,
                                volumeKeyPageTurn = settings.volumeKeyPageTurn
                            ),
                            volumeKeyPageTurnEnabled = settings.volumeKeyPageTurn &&
                                !showSettings &&
                                !showToc &&
                                !showSearch &&
                                !showBookmarks &&
                                ttsState != TtsPlaybackState.Speaking,
                            bg = bg,
                            fg = fg,
                            bookId = book?.id.orEmpty(),
                            playbackSession = playbackSession,
                            pendingSearchJump = pendingSearchJump,
                            pendingBookmarkJump = pendingBookmarkJump,
                            onBookmarkJumpConsumed = { pendingBookmarkJump = null },
                            suppressPageSync = suppressPageSync,
                            onSearchJumpConsumed = { pendingSearchJump = null },
                            initialCharacter = viewModel.textPosition?.takeIf { it.chapter == chapterIndex }?.character,
                            onPageProgress = { chapter, page, total, character ->
                                suppressPageSync = false
                                pageIndex = page
                                pageCount = total.coerceAtLeast(1)
                                viewModel.onEnginePageChanged(chapter, page, total, character)
                            },
                            onPageCharRangeProvider = { viewModel.pageCharRangeProvider = it },
                            onPageCharOffsetProvider = { provider ->
                                viewModel.pageCharOffsetProvider = provider
                            },
                            onCenterTap = { menuVisible = !menuVisible }
                        )
                        useReadium -> ReadiumPagerHost(
                            bookId = book?.id.orEmpty(),
                            onLocator = { json, percent ->
                                readiumPercent = percent
                                viewModel.persistLocator(json, percent)
                            }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.openSettings(false) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = panelBg,
            contentColor = fg,
            dragHandle = {
                androidx.compose.material3.BottomSheetDefaults.DragHandle(
                    color = fg.copy(alpha = 0.35f)
                )
            }
        ) {
            ReaderSettingsSheet(
                settings = settings,
                listeningEnabled = listeningEnabled,
                curlAvailable = curlAvailable,
                onFontScale = viewModel::updateFontScale,
                onTheme = viewModel::updateTheme,
                onBrightness = viewModel::updateBrightness,
                onPageTurn = viewModel::updatePageTurnEffect,
                onFontFamily = viewModel::updateFontFamily,
                onLetterSpacing = viewModel::updateLetterSpacing,
                onLineHeight = viewModel::updateLineHeight,
                onParagraphSpacing = viewModel::updateParagraphSpacing,
                onChineseMode = viewModel::updateChineseMode,
                showChineseScript = usePaginatedText,
                onVolumeKeyPageTurn = viewModel::updateVolumeKeyPageTurn,
                onImportFont = viewModel::importFont
            )
        }
    }

    if (showSearch && usePaginatedText) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.openSearch(false) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = panelBg
        ) {
            ReaderSearchSheet(
                viewModel = viewModel,
                contentColor = fg,
                onJump = { hit ->
                    suppressPageSync = true
                    pendingSearchJump = hit
                    viewModel.applySearchJump(hit)
                    menuVisible = false
                },
                onDismiss = { viewModel.openSearch(false) }
            )
        }
    }

    if (showBookmarks) {
        val bookmarks = annotations
            .filter { it.type == "bookmark" }
            .distinctBy { it.bookmarkPositionKey() }
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { viewModel.openBookmarks(false) },
            title = { Text(stringResource(R.string.reader_bookmarks)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (bookmarks.isEmpty()) {
                        Text(
                            stringResource(R.string.reader_bookmarks_empty),
                            color = HearTextSecondary
                        )
                    } else {
                        bookmarks.forEach { mark ->
                            val idx = mark.chapterIndex ?: 0
                            val page = (mark.startOffset ?: 0).coerceAtLeast(0)
                            val title = chapters.getOrNull(idx)?.title
                                    ?: stringResource(R.string.reader_chapter_n, idx + 1)
                            val isCurrent =
                                mark.matchesBookmarkPage(chapterIndex, pageIndex, viewModel.pageCharRangeProvider?.invoke())
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val position = TextPosition.decode(mark.locatorJson)?.takeIf { it.chapter == idx && idx in chapters.indices }
                                        if (position != null) {
                                            suppressPageSync = true
                                            pendingBookmarkJump = position
                                        } else {
                                            viewModel.selectChapter(idx)
                                            pageIndex = page
                                        }
                                        viewModel.openBookmarks(false)
                                        menuVisible = false
                                    }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(
                                    text = title,
                                    color = if (isCurrent) HearPurple else Color.Unspecified
                                )
                                Text(
                                    text = if (TextPosition.decode(mark.locatorJson) != null) stringResource(R.string.reader_saved_text_position)
                                        else stringResource(R.string.reader_page_n, page + 1),
                                    color = if (isCurrent) {
                                        HearPurple.copy(alpha = 0.75f)
                                    } else {
                                        HearTextSecondary
                                    },
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.openBookmarks(false) }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    if (showToc && chapters.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.openToc(false) },
            title = { Text(stringResource(R.string.reader_toc)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    chapters.forEachIndexed { index, chapter ->
                        Text(
                            text = chapter.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectChapter(index)
                                    pageIndex = 0
                                }
                                .padding(vertical = 10.dp),
                            color = if (index == chapterIndex) HearPurple else Color.Unspecified
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.openToc(false) }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}

private data class TypographyKey(
    val fontScale: Float,
    val fontFamily: ReaderFontFamily,
    val customFontPath: String?,
    val letterSpacing: Float,
    val lineHeight: Float,
    val paragraphSpacing: Float
)

/** Keep alpha = 1; darken toward black as brightness decreases. */
private fun dimReaderBackground(base: Color, brightness: Float): Color {
    val b = brightness.coerceIn(0.3f, 1f)
    return Color(
        red = base.red * b,
        green = base.green * b,
        blue = base.blue * b,
        alpha = 1f
    )
}

private fun opaqueArgb(color: Color): Int {
    return android.graphics.Color.argb(
        255,
        (color.red * 255).toInt().coerceIn(0, 255),
        (color.green * 255).toInt().coerceIn(0, 255),
        (color.blue * 255).toInt().coerceIn(0, 255)
    )
}

private fun readerThemeKey(mode: ReaderThemeMode): String = when (mode) {
    ReaderThemeMode.DARK -> "night"
    ReaderThemeMode.SEPIA -> "sepia"
    ReaderThemeMode.LIGHT -> "day"
}

private fun fontTypeKey(family: ReaderFontFamily): String =
    com.yishenghuang.heartext.data.ReaderTypefaces.fontTypeKey(family)

private fun pageTransitionKey(effect: PageTurnEffect): String = when (effect) {
    PageTurnEffect.FADE -> "fade"
    PageTurnEffect.CURL -> "curl"
    PageTurnEffect.SLIDE -> "slide"
}

@Composable
private fun LumiReadHost(
    chapters: List<EpubChapter>,
    chapterIndex: Int,
    pageIndex: Int,
    settings: ReaderSettings,
    volumeKeyPageTurnEnabled: Boolean,
    bg: Color,
    fg: Color,
    bookId: String,
    playbackSession: PlaybackSession,
    pendingSearchJump: ReaderSearchHit?,
    pendingBookmarkJump: TextPosition?,
    onBookmarkJumpConsumed: () -> Unit,
    suppressPageSync: Boolean,
    onSearchJumpConsumed: () -> Unit,
    initialCharacter: Int?,
    onPageProgress: (chapter: Int, page: Int, total: Int, character: Int?) -> Unit,
    onPageCharOffsetProvider: ((() -> Int?)?) -> Unit = {},
    onPageCharRangeProvider: ((() -> IntRange?)?) -> Unit = {},
    onCenterTap: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val safeTop = rememberReaderSafeTop()
    val safeBottom = rememberReaderSafeBottom()
    val chapterSnapshot = remember(chapters) { chapters.toList() }
    val readViewRef = remember { mutableStateOf<ReadView?>(null) }

    DisposableEffect(volumeKeyPageTurnEnabled, readViewRef.value) {
        val activity = context as? MainActivity
        if (activity == null || !volumeKeyPageTurnEnabled) {
            return@DisposableEffect onDispose { }
        }
        activity.keyEventInterceptor = interceptor@{ event ->
            if (event.action != android.view.KeyEvent.ACTION_DOWN) return@interceptor false
            val view = readViewRef.value ?: return@interceptor false
            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                    view.turnToPreviousPage()
                    true
                }
                android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    view.turnToNextPage()
                    true
                }
                else -> false
            }
        }
        onDispose {
            activity.keyEventInterceptor = null
        }
    }

    LaunchedEffect(pendingBookmarkJump, readViewRef.value) {
        val position = pendingBookmarkJump ?: return@LaunchedEffect
        val view = readViewRef.value ?: return@LaunchedEffect
        view.jumpToCharacter(position.chapter, position.character)
        onBookmarkJumpConsumed()
    }

    // Search jumps take priority and must not be overwritten by pageIndex sync.
    LaunchedEffect(pendingSearchJump, readViewRef.value) {
        val jump = pendingSearchJump ?: return@LaunchedEffect
        val view = readViewRef.value ?: return@LaunchedEffect
        view.jumpToSearchResult(jump.chapterIndex, jump.startOffset, jump.matchLength)
        onSearchJumpConsumed()
    }

    // TOC / bookmark jumps: drive ReadView when chapterIndex changes from outside.
    LaunchedEffect(chapterIndex, pageIndex, suppressPageSync, pendingSearchJump) {
        if (pendingSearchJump != null || suppressPageSync) return@LaunchedEffect
        val view = readViewRef.value ?: return@LaunchedEffect
        val loc = view.getCurrentLocation() ?: return@LaunchedEffect
        if (loc.first != chapterIndex || loc.second != pageIndex) {
            view.jumpToChapter(chapterIndex, pageIndex.coerceAtLeast(0))
        }
    }

    // Spoken-sentence highlight + follow page while listening to this book.
    LaunchedEffect(
        playbackSession.active,
        playbackSession.bookId,
        playbackSession.chapterIndex,
        playbackSession.spokenStart,
        playbackSession.spokenEnd,
        playbackSession.playbackState,
        bookId
    ) {
        val view = readViewRef.value ?: return@LaunchedEffect
        val listeningThisBook = playbackSession.active &&
            playbackSession.bookId == bookId &&
            (playbackSession.playbackState == TtsPlaybackState.Speaking ||
                playbackSession.playbackState == TtsPlaybackState.Paused)
        if (!listeningThisBook || playbackSession.spokenEnd <= playbackSession.spokenStart) {
            view.clearTtsHighlight()
            return@LaunchedEffect
        }
        view.setTtsHighlight(
            chapterIndex = playbackSession.chapterIndex,
            start = playbackSession.spokenStart,
            end = playbackSession.spokenEnd,
            followPage = playbackSession.playbackState == TtsPlaybackState.Speaking
        )
    }

    AndroidView(
        factory = { ctx ->
            ReadView(ctx).apply {
                setCallbacks(object : ReadViewCallbacks {
                    override fun onPageChanged(
                        globalPage: Int,
                        chapterIndex: Int,
                        pageInChapter: Int,
                        chapterTotalPages: Int
                    ) {
                        onPageProgress(chapterIndex, pageInChapter, chapterTotalPages, getCurrentPageStartCharacterOffset())
                    }

                    override fun onMenuToggle() {
                        onCenterTap()
                    }

                    override fun onLoadingChanged(isLoading: Boolean) {}
                })
                setContentProvider { index ->
                    chapterSnapshot.getOrNull(index)?.plainText
                }
                readViewRef.value = this
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { readView ->
            readViewRef.value = readView
            val fontSizePx = with(density) { (17f * settings.fontScale).sp.toPx() }
            val densityScale = density.density
            val letterSpacingDp =
                if (fontSizePx > 0f) settings.letterSpacing * fontSizePx / densityScale else 0f
            val paragraphSpacingDp = settings.paragraphSpacing * 8f
            val themeKey = readerThemeKey(settings.themeMode)

            readView.setContentProvider { index ->
                chapterSnapshot.getOrNull(index)?.plainText
            }
            if (!(suppressPageSync && readView.getCurrentLocation() != null)) readView.configure(
                fontSizePx = fontSizePx,
                theme = themeKey,
                chapterCount = chapterSnapshot.size,
                startChapter = chapterIndex.coerceIn(0, (chapterSnapshot.size - 1).coerceAtLeast(0)),
                startPage = pageIndex.coerceAtLeast(0),
                initialCharacter = initialCharacter,
                lineHeightMult = settings.lineHeight,
                letterSpacingDp = letterSpacingDp,
                fontType = fontTypeKey(settings.fontFamily),
                customFontPath = settings.customFontPath,
                marginLeftDp = 28f,
                marginRightDp = 28f,
                marginTopDp = 28f,
                marginBottomDp = 28f,
                topOverlayInsetDp = safeTop.value,
                bottomOverlayInsetDp = safeBottom.value,
                paragraphSpacingDp = paragraphSpacingDp
            )
            readView.setReaderBackground(
                backgroundColor = opaqueArgb(bg),
                textColor = opaqueArgb(fg),
                imagePath = null
            )
            readView.setPageTransition(pageTransitionKey(settings.pageTurnEffect))
            readView.setChineseMode(settings.chineseMode)
            onPageCharRangeProvider { readView.getCurrentPageCharacterRange() }
            onPageCharOffsetProvider {
                readView.getCurrentPageStartCharacterOffset()
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            onPageCharRangeProvider(null)
            onPageCharOffsetProvider(null)
            readViewRef.value?.clearTtsHighlight()
            readViewRef.value = null
        }
    }
}

@Composable
private fun ReadiumPagerHost(
    bookId: String,
    onLocator: (json: String, percent: Float) -> Unit
) {
    val activity = androidx.activity.compose.LocalActivity.current as? FragmentActivity ?: return
    val containerId = rememberSaveable { android.view.View.generateViewId() }
    val scope = rememberCoroutineScope()
    val latestOnLocator by rememberUpdatedState(onLocator)

    DisposableEffect(activity, bookId) {
        val observer = activity.supportFragmentManager.observeReadiumLocators("readium_$bookId", scope) {
            latestOnLocator(ReadiumHostFragment.locatorToJson(it), ReadiumHostFragment.progressionPercent(it))
        }
        onDispose {
            observer.close()
            val existing = activity.supportFragmentManager.findFragmentByTag("readium_$bookId")
            if (existing != null) {
                activity.supportFragmentManager.commit(allowStateLoss = true) {
                    remove(existing)
                }
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            FragmentContainerView(ctx).apply {
                id = containerId
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { container ->
            val tag = "readium_$bookId"
            val fm = activity.supportFragmentManager
            // Restored fragments can create their views before the publication has
            // reopened and Compose adds this container. Attach that waiting view now.
            fm.onContainerAvailable(container)
            val existing = fm.findFragmentByTag(tag) as? ReadiumHostFragment
            if (existing != null) {
                existing.bindAvailableSession()
            } else if (bookId.isNotBlank()) {
                fm.commit {
                    replace(container.id, ReadiumHostFragment.newInstance(bookId), tag)
                }
            }
        }
    )


}
