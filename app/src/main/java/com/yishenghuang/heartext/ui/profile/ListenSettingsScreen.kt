package com.yishenghuang.heartext.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yishenghuang.heartext.OfflineDownloadPhase
import com.yishenghuang.heartext.ProfileViewModel
import com.yishenghuang.heartext.R
import com.yishenghuang.heartext.data.TtsVoiceSource
import com.yishenghuang.heartext.ui.theme.AppColors
import com.yishenghuang.heartext.ui.theme.AppSpace
import com.yishenghuang.heartext.ui.theme.HearPurple
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenSettingsScreen(
    viewModel: ProfileViewModel,
    authSignedIn: Boolean,
    onBack: () -> Unit
) {
    val offlineVoices by viewModel.offlineVoices.collectAsStateWithLifecycle()
    val installedIds by viewModel.installedIds.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val readerSettings by viewModel.settings.collectAsStateWithLifecycle()
    val samplePlayingId by viewModel.samplePlayingId.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val inUseLabel = stringResource(R.string.listen_in_use)
    val installingLabel = stringResource(R.string.listen_installing)
    val downloadingLabel = stringResource(R.string.listen_downloading)
    val redownloadLabel = stringResource(R.string.listen_redownload)
    val downloadLabel = stringResource(R.string.listen_download)
    val selectLabel = stringResource(R.string.listen_select)
    val stopLabel = stringResource(R.string.reader_stop)
    val playLabel = stringResource(R.string.reader_play)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.stopSample()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopSample()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.listen_settings_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = AppColors.WindowBg,
                titleContentColor = AppColors.TextPrimary,
                navigationIconContentColor = AppColors.TextPrimary
            )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpace.lg)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.listen_voice_title),
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextPrimary
            )
            Text(
                stringResource(R.string.listen_voice_blurb),
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            val usingSystem = readerSettings.ttsVoiceSource == TtsVoiceSource.SYSTEM
            TextButton(
                onClick = { viewModel.selectSystemVoice() },
                enabled = !usingSystem
            ) {
                Text(
                    if (usingSystem) stringResource(R.string.listen_system_in_use)
                    else stringResource(R.string.listen_use_system)
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.listen_offline_packs),
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextPrimary
            )
            Text(
                stringResource(R.string.listen_offline_blurb),
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            if (offlineVoices.isEmpty()) {
                Text(
                    if (authSignedIn) stringResource(R.string.listen_no_packs)
                    else stringResource(R.string.listen_sign_in_packs),
                    color = AppColors.TextSecondary
                )
            } else {
                offlineVoices.forEach { voice ->
                    val installed = voice.id in installedIds
                    val progress = downloadProgress?.takeIf { it.voiceId == voice.id }
                    val selected = readerSettings.ttsVoiceSource == TtsVoiceSource.OFFLINE &&
                        readerSettings.selectedOfflineVoiceId == voice.id
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            voice.name + if (selected) " · $inUseLabel" else "",
                            style = MaterialTheme.typography.titleSmall,
                            color = AppColors.TextPrimary
                        )
                        Text(
                            "${voice.language} · ${voice.engine} · ${formatBytes(voice.fileSizeBytes)}",
                            color = AppColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (progress != null) {
                            val label = when (progress.phase) {
                                OfflineDownloadPhase.Downloading -> {
                                    val pct = (progress.fraction * 100).roundToInt()
                                    context.getString(
                                        R.string.listen_downloading_pct,
                                        pct,
                                        formatBytes(progress.bytesRead) +
                                            if (progress.contentLength > 0) {
                                                " / ${formatBytes(progress.contentLength)}"
                                            } else {
                                                ""
                                            }
                                    )
                                }
                                OfflineDownloadPhase.Installing -> installingLabel
                            }
                            Text(label, style = MaterialTheme.typography.bodySmall, color = HearPurple)
                            if (progress.contentLength > 0 || progress.fraction > 0f) {
                                LinearProgressIndicator(
                                    progress = { progress.fraction },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = HearPurple
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = HearPurple
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (voice.hasSample) {
                                val isPlaying = samplePlayingId == voice.id
                                TextButton(
                                    onClick = {
                                        if (isPlaying) viewModel.stopSample()
                                        else viewModel.playSample(voice.id)
                                    },
                                    enabled = progress == null
                                ) {
                                    Text(if (isPlaying) stopLabel else playLabel)
                                }
                            }
                            TextButton(
                                onClick = { viewModel.downloadOfflineVoice(voice) },
                                enabled = !busy
                            ) {
                                Text(
                                    when {
                                        progress != null -> downloadingLabel
                                        installed -> redownloadLabel
                                        else -> downloadLabel
                                    }
                                )
                            }
                            if (installed) {
                                TextButton(
                                    onClick = { viewModel.selectOfflineVoice(voice) },
                                    enabled = progress == null
                                ) {
                                    Text(if (selected) inUseLabel else selectLabel)
                                }
                            }
                        }
                    }
                }
            }

            message?.let {
                Text(it, color = HearPurple)
                LaunchedEffect(it) {
                    kotlinx.coroutines.delay(3200)
                    viewModel.clearMessage()
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}
