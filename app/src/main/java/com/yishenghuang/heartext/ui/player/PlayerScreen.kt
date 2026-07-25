package com.yishenghuang.heartext.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yishenghuang.heartext.PlayerViewModel
import com.yishenghuang.heartext.R
import com.yishenghuang.heartext.data.TtsVoiceSource
import com.yishenghuang.heartext.tts.TtsPlaybackState
import com.yishenghuang.heartext.ui.theme.HearPurple
import com.yishenghuang.heartext.ui.theme.HearTextPrimary
import com.yishenghuang.heartext.ui.theme.HearTextSecondary
import java.io.File

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onOpenReader: (bookId: String) -> Unit
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val book by viewModel.book.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val offline by viewModel.installedOffline.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val speaking = session.playbackState == TtsPlaybackState.Speaking
    val snackbar = remember { SnackbarHostState() }
    val appName = stringResource(R.string.app_name)
    val chapterFallback = stringResource(R.string.reader_chapter_n, session.chapterIndex + 1)
    val idleLabel = stringResource(R.string.reader_tts_idle)
    val speakingLabel = stringResource(R.string.reader_tts_speaking)
    val pausedLabel = stringResource(R.string.reader_tts_paused)
    val errorLabel = stringResource(R.string.common_error)
    val speedPrefix = stringResource(R.string.player_speed)
    val displayTitle = session.title.ifBlank { book?.title.orEmpty().ifBlank { appName } }
    val displayCover = session.coverPath ?: book?.coverPath
    val displayChapter = session.chapterTitle.ifBlank {
        if (session.active && session.chapterCount > 0) chapterFallback else idleLabel
    }
    val pauseLabel = stringResource(R.string.reader_pause)
    val playLabel = stringResource(R.string.reader_play)

    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it)
            viewModel.clearToast()
        }
    }
    LaunchedEffect(session.message) {
        session.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearSessionMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF3F0FF),
                        Color(0xFFFAFAFC),
                        Color(0xFFFFFFFF)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.player_now_playing),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = HearTextPrimary
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp)
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(HearPurple.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                val cover = displayCover
                if (cover != null && File(cover).exists()) {
                    AsyncImage(
                        model = File(cover),
                        contentDescription = displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = displayTitle.take(1).ifBlank { "H" },
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = HearPurple
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = displayTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = HearTextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = displayChapter,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = HearTextPrimary.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            val sentenceCount = session.sentenceCount
            val canSeek = sentenceCount > 1
            var scrubIndex by remember(session.bookId, session.chapterIndex) {
                mutableFloatStateOf(session.sentenceIndex.toFloat())
            }
            var scrubbing by remember { mutableStateOf(false) }
            LaunchedEffect(session.sentenceIndex, scrubbing) {
                if (!scrubbing) scrubIndex = session.sentenceIndex.toFloat()
            }
            val sliderValue = if (scrubbing) scrubIndex else session.sentenceIndex.toFloat()
            Slider(
                value = if (canSeek) sliderValue.coerceIn(0f, (sentenceCount - 1).toFloat()) else 0f,
                onValueChange = { value ->
                    if (!canSeek) return@Slider
                    scrubbing = true
                    scrubIndex = value
                },
                onValueChangeFinished = {
                    if (canSeek) {
                        viewModel.seekSentence(scrubIndex.toInt())
                    }
                    scrubbing = false
                },
                valueRange = if (canSeek) 0f..(sentenceCount - 1).toFloat() else 0f..1f,
                enabled = canSeek,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = HearPurple,
                    activeTrackColor = HearPurple,
                    inactiveTrackColor = HearPurple.copy(alpha = 0.15f)
                )
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (session.sentenceCount > 0) {
                    "${session.sentenceIndex + 1} / ${session.sentenceCount}"
                } else {
                    when (session.playbackState) {
                        TtsPlaybackState.Speaking -> speakingLabel
                        TtsPlaybackState.Paused -> pausedLabel
                        TtsPlaybackState.Error -> session.message ?: errorLabel
                        else -> idleLabel
                    }
                },
                fontSize = 12.sp,
                color = HearTextSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$speedPrefix ${PlayerViewModel.formatSpeed(settings.ttsSpeed)}",
                    color = HearPurple,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(HearPurple.copy(alpha = 0.1f))
                        .clickable { viewModel.cycleSpeechRate() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.skipChapter(-1) },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.catalog_prev_chapter),
                        modifier = Modifier.size(36.dp),
                        tint = HearTextPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(HearPurple)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { viewModel.playPause() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (speaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (speaking) pauseLabel else playLabel,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(
                    onClick = { viewModel.skipChapter(+1) },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.catalog_next_chapter),
                        modifier = Modifier.size(36.dp),
                        tint = HearTextPrimary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    text = stringResource(R.string.reader_stop),
                    color = HearTextSecondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { viewModel.stop() }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            if (session.bookId.isNotBlank()) onOpenReader(session.bookId)
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = HearPurple,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        stringResource(R.string.player_open_reader),
                        color = HearPurple,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.listen_voice_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VoiceSourceChip(
                    label = stringResource(R.string.reader_system_short),
                    selected = settings.ttsVoiceSource == TtsVoiceSource.SYSTEM,
                    onClick = { viewModel.selectSystemVoice() }
                )
                VoiceSourceChip(
                    label = stringResource(R.string.reader_offline_short),
                    selected = settings.ttsVoiceSource == TtsVoiceSource.OFFLINE,
                    onClick = {
                        val id = settings.selectedOfflineVoiceId
                            ?: offline.firstOrNull()?.id
                        if (id != null) {
                            viewModel.selectOfflineVoice(id)
                        } else {
                            viewModel.refreshOffline()
                        }
                    }
                )
            }

            if (settings.ttsVoiceSource == TtsVoiceSource.OFFLINE) {
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.listen_offline_packs),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(8.dp))
                if (offline.isEmpty()) {
                    Text(
                        stringResource(R.string.player_no_offline),
                        color = HearTextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        offline.forEach { voice ->
                            VoiceSourceChip(
                                label = voice.name,
                                selected = settings.selectedOfflineVoiceId == voice.id,
                                onClick = { viewModel.selectOfflineVoice(voice.id) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun VoiceSourceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) HearPurple else Color(0xFFF2F2F7))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            color = if (selected) Color.White else Color(0xFF1A1A2E),
            fontWeight = FontWeight.Medium
        )
    }
}
