package com.yishenghuang.heartext.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yishenghuang.heartext.R
import com.yishenghuang.heartext.tts.TtsPlaybackState
import com.yishenghuang.heartext.ui.theme.HearPurple

/** Stable top inset under immersive mode (status bar / punch-hole), never collapses to 0. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rememberReaderSafeTop(): Dp {
    val density = LocalDensity.current
    val insets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
    val topPx = insets.getTop(density)
    return with(density) { topPx.toDp() }.coerceAtLeast(36.dp)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rememberReaderSafeBottom(): Dp {
    val density = LocalDensity.current
    val insets = WindowInsets.navigationBarsIgnoringVisibility
    val bottomPx = insets.getBottom(density)
    return with(density) { bottomPx.toDp() }.coerceAtLeast(16.dp)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderImmersiveChrome(
    menuVisible: Boolean,
    title: String,
    chapterTitle: String,
    progressPercent: Float,
    pageLabel: String,
    bg: Color,
    fg: Color,
    panelBg: Color,
    listeningEnabled: Boolean,
    ttsState: TtsPlaybackState,
    ttsSourceLabel: String,
    ttsSpeed: Float = 1f,
    onBack: () -> Unit,
    onToggleListen: () -> Unit,
    onListenFromPage: () -> Unit,
    onStopListen: () -> Unit,
    onCycleSpeed: () -> Unit = {},
    onOpenPlayer: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onAddBookmark: () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
    isCurrentPageBookmarked: Boolean = false,
    bookmarkEnabled: Boolean = true,
    onOpenToc: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleMenu: () -> Unit,
    showCenterTapOverlay: Boolean = true,
    pageContent: @Composable () -> Unit
) {
    val isListening =
        ttsState == TtsPlaybackState.Speaking ||
            ttsState == TtsPlaybackState.Paused ||
            ttsState == TtsPlaybackState.Error

    Box(modifier = Modifier.fillMaxSize()) {
        pageContent()

        if (!menuVisible) {
            ReaderCornerOverlay(
                chapterTitle = chapterTitle,
                progressPercent = progressPercent,
                contentColor = fg.copy(alpha = 0.45f),
                bottomExtra = if (listeningEnabled && isListening) 72.dp else 0.dp
            )
        }

        if (showCenterTapOverlay && !menuVisible) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.4f)
                    .align(Alignment.Center)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onToggleMenu
                    )
            )
        }

        if (menuVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onToggleMenu
                    )
            )
        }

        AnimatedVisibility(
            visible = menuVisible,
            enter = fadeIn() + slideInVertically { -it / 3 },
            exit = fadeOut() + slideOutVertically { -it / 3 },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ReaderTopChrome(
                title = title,
                bg = bg,
                fg = fg,
                canSearch = listeningEnabled, // text books only (same gate as listen)
                onBack = onBack,
                onSearch = onOpenSearch,
                onAddBookmark = onAddBookmark,
                isCurrentPageBookmarked = isCurrentPageBookmarked,
                bookmarkEnabled = bookmarkEnabled
            )
        }

        // Compact mini-player only — no extra chips when reading immersively.
        AnimatedVisibility(
            visible = listeningEnabled && !menuVisible && isListening,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            ReaderListenBar(
                ttsState = ttsState,
                ttsSourceLabel = ttsSourceLabel,
                ttsSpeed = ttsSpeed,
                panelBg = panelBg,
                fg = fg,
                onPlayPause = onToggleListen,
                onStop = onStopListen,
                onOpenPlayer = onOpenPlayer,
                onListenFromPage = onListenFromPage,
                onCycleSpeed = onCycleSpeed,
                showFromPage = true
            )
        }

        AnimatedVisibility(
            visible = menuVisible,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, bg)))
                )
                ReaderBottomDock(
                    chapterTitle = chapterTitle,
                    pageLabel = pageLabel,
                    progressPercent = progressPercent,
                    bg = bg,
                    fg = fg,
                    panelBg = panelBg,
                    listeningEnabled = listeningEnabled,
                    ttsState = ttsState,
                    ttsSourceLabel = ttsSourceLabel,
                    ttsSpeed = ttsSpeed,
                    onOpenToc = onOpenToc,
                    onOpenSettings = onOpenSettings,
                    onOpenBookmarks = onOpenBookmarks,
                    onListenFromPage = onListenFromPage,
                    onToggleListen = onToggleListen,
                    onStopListen = onStopListen,
                    onCycleSpeed = onCycleSpeed,
                    onOpenPlayer = onOpenPlayer
                )
            }
        }
    }
}

/** Single bottom surface: meta + progress + actions (+ listen strip when active). */
@Composable
private fun ReaderBottomDock(
    chapterTitle: String,
    pageLabel: String,
    progressPercent: Float,
    bg: Color,
    fg: Color,
    panelBg: Color,
    listeningEnabled: Boolean,
    ttsState: TtsPlaybackState,
    ttsSourceLabel: String,
    ttsSpeed: Float,
    onOpenToc: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onListenFromPage: () -> Unit,
    onToggleListen: () -> Unit,
    onStopListen: () -> Unit,
    onCycleSpeed: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val muted = fg.copy(alpha = 0.55f)
    val isListening =
        ttsState == TtsPlaybackState.Speaking ||
            ttsState == TtsPlaybackState.Paused ||
            ttsState == TtsPlaybackState.Error

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .navigationBarsPadding()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {}
            )
            .padding(horizontal = 20.dp)
            .padding(bottom = 14.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tocFallback = stringResource(R.string.reader_toc)
            Text(
                text = chapterTitle.ifBlank { tocFallback },
                color = muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = listOfNotNull(
                    pageLabel.takeIf { it.isNotBlank() },
                    "%.0f%%".format(progressPercent.coerceIn(0f, 100f))
                ).joinToString(" · "),
                color = muted,
                fontSize = 12.sp
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(fg.copy(alpha = 0.08f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onOpenToc
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((progressPercent / 100f).coerceIn(0.02f, 1f))
                    .background(HearPurple.copy(alpha = 0.75f))
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DockAction(
                icon = Icons.AutoMirrored.Filled.List,
                label = stringResource(R.string.reader_toc),
                fg = fg,
                onClick = onOpenToc
            )
            DockAction(
                icon = Icons.Default.Settings,
                label = stringResource(R.string.typography_title),
                fg = fg,
                onClick = onOpenSettings
            )
            DockAction(
                icon = Icons.Default.Bookmark,
                label = stringResource(R.string.reader_bookmarks),
                fg = fg,
                onClick = onOpenBookmarks
            )
            if (listeningEnabled) {
                DockAction(
                    icon = Icons.Default.Headphones,
                    label = if (isListening) stringResource(R.string.reader_listening)
                    else stringResource(R.string.reader_listen),
                    fg = if (isListening) HearPurple else fg,
                    onClick = onOpenPlayer
                )
            }
        }

        // White bar only while listening.
        if (listeningEnabled && isListening) {
            ReaderListenBar(
                ttsState = ttsState,
                ttsSourceLabel = ttsSourceLabel,
                ttsSpeed = ttsSpeed,
                panelBg = panelBg,
                fg = fg,
                onPlayPause = onToggleListen,
                onStop = onStopListen,
                onOpenPlayer = onOpenPlayer,
                onListenFromPage = onListenFromPage,
                onCycleSpeed = onCycleSpeed,
                showFromPage = true
            )
        }
    }
}

@Composable
private fun DockAction(
    icon: ImageVector,
    label: String,
    fg: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = fg.copy(alpha = 0.75f), fontSize = 11.sp)
    }
}

/** One compact listen control — play/pause, status, optional from-page, stop. */
@Composable
private fun ReaderListenBar(
    ttsState: TtsPlaybackState,
    ttsSourceLabel: String,
    ttsSpeed: Float,
    panelBg: Color,
    fg: Color,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onOpenPlayer: () -> Unit,
    onListenFromPage: () -> Unit,
    onCycleSpeed: () -> Unit,
    showFromPage: Boolean = true
) {
    val speaking = ttsState == TtsPlaybackState.Speaking
    val status = when (ttsState) {
        TtsPlaybackState.Speaking -> stringResource(R.string.reader_tts_speaking)
        TtsPlaybackState.Paused -> stringResource(R.string.reader_tts_paused)
        TtsPlaybackState.Error -> stringResource(R.string.reader_tts_error)
        else -> stringResource(R.string.reader_tts_idle)
    }
    val speedLabel = formatTtsSpeed(ttsSpeed)
    val pauseLabel = stringResource(R.string.reader_pause)
    val playLabel = stringResource(R.string.reader_play)
    val playerHint = stringResource(R.string.player_now_playing)
    val listenPageLabel = stringResource(R.string.reader_listen_page)
    val stopLabel = stringResource(R.string.reader_stop)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(panelBg)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when {
                speaking -> Icons.Default.Pause
                else -> Icons.Default.PlayArrow
            },
            contentDescription = if (speaking) pauseLabel else playLabel,
            tint = HearPurple,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onPlayPause
                )
                .padding(10.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onOpenPlayer
                )
                .padding(horizontal = 4.dp)
        ) {
            Text(
                text = status,
                color = fg,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = "$ttsSourceLabel · $playerHint",
                color = fg.copy(alpha = 0.5f),
                fontSize = 11.sp,
                maxLines = 1
            )
        }

        Text(
            text = speedLabel,
            color = HearPurple,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onCycleSpeed
                )
                .padding(horizontal = 8.dp, vertical = 8.dp)
        )

        if (showFromPage) {
            Text(
                text = listenPageLabel,
                color = HearPurple,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onListenFromPage
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }

        Icon(
            Icons.Default.Close,
            contentDescription = stopLabel,
            tint = fg.copy(alpha = 0.55f),
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onStop
                )
                .padding(10.dp)
        )
    }
}

private fun formatTtsSpeed(speed: Float): String {
    val v = if (kotlin.math.abs(speed - speed.toInt()) < 0.01f) {
        "${speed.toInt()}"
    } else {
        String.format(java.util.Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')
    }
    return "${v}x"
}

@Composable
private fun ReaderTopChrome(
    title: String,
    bg: Color,
    fg: Color,
    canSearch: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onAddBookmark: () -> Unit,
    isCurrentPageBookmarked: Boolean,
    bookmarkEnabled: Boolean
) {
    val safeTop = rememberReaderSafeTop()
    val controlBg = if (fg == Color.White) {
        Color.Black.copy(alpha = 0.28f)
    } else {
        Color(0xFFE8E8EE).copy(alpha = 0.85f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(top = safeTop)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChromeIconButton(
                Icons.AutoMirrored.Filled.ArrowBack,
                stringResource(R.string.back),
                fg,
                controlBg,
                onBack
            )
            Text(
                text = title,
                color = fg.copy(alpha = 0.7f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canSearch) {
                    ChromeIconButton(
                        Icons.Default.Search,
                        stringResource(R.string.reader_search),
                        fg,
                        controlBg,
                        onSearch
                    )
                }
                val bookmarkTint = if (isCurrentPageBookmarked) HearPurple else fg.copy(alpha = 0.55f)
                val bookmarkIcon =
                    if (isCurrentPageBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder
                val bookmarkDesc = if (isCurrentPageBookmarked) {
                    stringResource(R.string.reader_remove_bookmark)
                } else {
                    stringResource(R.string.reader_add_bookmark)
                }
                ChromeIconButton(bookmarkIcon, bookmarkDesc, bookmarkTint, controlBg, onAddBookmark, bookmarkEnabled)
            }
        }
    }
}

@Composable
private fun ChromeIconButton(
    icon: ImageVector,
    desc: String,
    tint: Color,
    background: Color,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(
                enabled = enabled,
                role = androidx.compose.ui.semantics.Role.Button,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = if (enabled) tint else tint.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderCornerOverlay(
    chapterTitle: String,
    progressPercent: Float,
    contentColor: Color,
    bottomExtra: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val safeTop = rememberReaderSafeTop()
    val safeBottom = rememberReaderSafeBottom()
    Box(modifier = modifier.fillMaxSize()) {
        if (chapterTitle.isNotBlank()) {
            Text(
                text = chapterTitle,
                color = contentColor,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 28.dp, top = (safeTop - 4.dp).coerceAtLeast(8.dp), end = 96.dp)
            )
        }
        Text(
            text = "%.0f%%".format(progressPercent.coerceIn(0f, 100f)),
            color = contentColor,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 28.dp, bottom = safeBottom + 12.dp + bottomExtra)
        )
    }
}
