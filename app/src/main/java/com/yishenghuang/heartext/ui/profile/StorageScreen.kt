package com.yishenghuang.heartext.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.yishenghuang.heartext.data.StorageRepository
import com.yishenghuang.heartext.data.StorageUsage
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yishenghuang.heartext.R
import com.yishenghuang.heartext.ui.theme.AppColors
import com.yishenghuang.heartext.ui.theme.HearPurple

@Composable
fun StorageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val repository = remember(context) { StorageRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var usage by remember { mutableStateOf<StorageUsage?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(true) }
    var clearing by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableStateOf(0) }

    LaunchedEffect(refreshToken) {
        refreshing = true
        try { usage = repository.measure() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { message = resources.getString(R.string.storage_failed) }
        finally { refreshing = false }
    }

    SettingsSubpageScaffold(title = stringResource(R.string.storage_title), onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(16.dp)
        ) {
            Text(
                stringResource(R.string.storage_total),
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (refreshing) stringResource(R.string.storage_calculating) else usage?.let { formatBytes(it.total) }.orEmpty(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
        }

        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(vertical = 4.dp)
        ) {
            StorageRow(stringResource(R.string.storage_books), usage?.books ?: 0)
            StorageRow(stringResource(R.string.storage_covers), usage?.covers ?: 0)
            StorageRow(stringResource(R.string.storage_catalog), usage?.catalog ?: 0)
            StorageRow(stringResource(R.string.storage_voices), usage?.voices ?: 0)
            StorageRow(stringResource(R.string.storage_fonts), usage?.fonts ?: 0)
            StorageRow(stringResource(R.string.storage_cache), usage?.cache ?: 0)
            StorageRow(stringResource(R.string.storage_other), usage?.other ?: 0, showDivider = false)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.storage_clear_hint),
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (!clearing) {
                    clearing = true
                    message = null
                    scope.launch {
                        try {
                            val cleared = repository.clearImageCache()
                            message = resources.getString(R.string.storage_cleared,
                                android.text.format.Formatter.formatFileSize(context, cleared))
                            refreshToken++
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (_: Exception) { message = resources.getString(R.string.storage_failed) }
                        finally { clearing = false }
                    }
                }
            },
            enabled = !clearing && !refreshing,
            colors = ButtonDefaults.buttonColors(containerColor = HearPurple),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(if (clearing) R.string.storage_clearing else R.string.storage_clear_cache))
        }

        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = HearPurple)
        }
    }
}

@Composable
private fun StorageRow(label: String, bytes: Long, showDivider: Boolean = true) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(label, modifier = Modifier.weight(1f), color = AppColors.TextPrimary)
            Text(formatBytes(bytes), color = AppColors.TextSecondary)
        }
        if (showDivider) {
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                thickness = 0.5.dp,
                color = Color(0xFFE5E5EA)
            )
        }
    }
}

@Composable
private fun formatBytes(bytes: Long): String =
    android.text.format.Formatter.formatFileSize(LocalContext.current, bytes)
