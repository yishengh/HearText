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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun StorageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    var booksBytes by remember { mutableLongStateOf(0L) }
    var coversBytes by remember { mutableLongStateOf(0L) }
    var catalogBytes by remember { mutableLongStateOf(0L) }
    var voicesBytes by remember { mutableLongStateOf(0L) }
    var fontsBytes by remember { mutableLongStateOf(0L) }
    var otherBytes by remember { mutableLongStateOf(0L) }
    var message by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(true) }
    var refreshToken by remember { mutableStateOf(0) }

    LaunchedEffect(refreshToken) {
        refreshing = true
        withContext(Dispatchers.IO) {
            val files = context.filesDir
            booksBytes = dirSize(File(files, "books"))
            coversBytes = dirSize(File(files, "covers"))
            catalogBytes = dirSize(File(files, "catalog"))
            voicesBytes = dirSize(File(files, "offline_voices")) +
                dirSize(File(files, "voices")) +
                dirSize(File(files, "sherpa"))
            fontsBytes = dirSize(File(files, "fonts"))
            val known = setOf("books", "covers", "catalog", "offline_voices", "voices", "sherpa", "fonts")
            otherBytes = files.listFiles()
                ?.filter { it.isDirectory && it.name !in known }
                ?.sumOf { dirSize(it) }
                ?: 0L
        }
        refreshing = false
    }

    val total = booksBytes + coversBytes + catalogBytes + voicesBytes + fontsBytes + otherBytes

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
                if (refreshing) stringResource(R.string.storage_calculating) else formatBytes(total),
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
            StorageRow(stringResource(R.string.storage_books), booksBytes)
            StorageRow(stringResource(R.string.storage_covers), coversBytes)
            StorageRow(stringResource(R.string.storage_catalog), catalogBytes)
            StorageRow(stringResource(R.string.storage_voices), voicesBytes)
            StorageRow(stringResource(R.string.storage_fonts), fontsBytes, showDivider = false)
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
                val cleared = runCatching {
                    val cache = File(context.cacheDir.absolutePath)
                    dirSize(cache).also {
                        cache.listFiles()?.forEach { child ->
                            runCatching { child.deleteRecursively() }
                        }
                    }
                }.getOrDefault(0L)
                // Light catalog re-download leftovers under temp if any
                runCatching {
                    File(context.filesDir, "catalog/.tmp").takeIf { it.exists() }?.deleteRecursively()
                }
                message = resources.getString(R.string.storage_cleared, formatBytes(cleared))
                refreshToken++
            },
            colors = ButtonDefaults.buttonColors(containerColor = HearPurple),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.storage_clear_cache))
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

private fun dirSize(dir: File): Long {
    if (!dir.exists()) return 0L
    if (dir.isFile) return dir.length()
    return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}
