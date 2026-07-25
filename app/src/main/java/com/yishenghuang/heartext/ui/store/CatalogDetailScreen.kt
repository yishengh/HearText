package com.yishenghuang.heartext.ui.store

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yishenghuang.heartext.CatalogDetailViewModel
import com.yishenghuang.heartext.R
import com.yishenghuang.heartext.ui.theme.AppColors
import com.yishenghuang.heartext.ui.theme.HearPurple

@Composable
fun CatalogDetailScreen(
    viewModel: CatalogDetailViewModel,
    onBack: () -> Unit,
    onPreview: (String) -> Unit,
    onOpenShelved: (bookId: String, title: String, cover: String?) -> Unit
) {
    val book by viewModel.book.collectAsStateWithLifecycle()
    val toc by viewModel.toc.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val shelved by viewModel.shelvedBook.collectAsStateWithLifecycle()
    val previewSuffix = stringResource(R.string.catalog_preview_suffix)

    LaunchedEffect(shelved) {
        val b = shelved ?: return@LaunchedEffect
        viewModel.consumeShelvedBook()
        onOpenShelved(b.id, b.title, b.coverPath)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
            .statusBarsPadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
            Text(stringResource(R.string.catalog_detail_title), style = MaterialTheme.typography.titleLarge)
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = HearPurple)
            }
            book == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: stringResource(R.string.common_not_found))
            }
            else -> {
                val b = book!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                        .padding(bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(
                            modifier = Modifier
                                .size(120.dp, 168.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(HearPurple.copy(alpha = 0.12f))
                        ) {
                            if (!b.coverUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = b.coverUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(b.title, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                b.author ?: stringResource(R.string.store_unknown_author),
                                color = AppColors.TextSecondary
                            )
                            Spacer(Modifier.height(8.dp))
                            val categories = b.categories.joinToString(" · ") { it.name }
                            val meta = stringResource(
                                R.string.catalog_preview_meta,
                                b.previewChapterLimit,
                                (b.fileSizeBytes / 1024).toString()
                            )
                            Text(
                                if (categories.isBlank()) meta else "$categories\n$meta",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextSecondary
                            )
                        }
                    }
                    Text(
                        b.description ?: stringResource(R.string.catalog_no_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { onPreview(b.id) },
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.catalog_preview)) }
                        Button(
                            onClick = viewModel::downloadAndShelf,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = HearPurple)
                        ) {
                            if (busy) CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = androidx.compose.ui.graphics.Color.White
                            ) else Text(stringResource(R.string.catalog_download))
                        }
                    }
                    Text(stringResource(R.string.reader_toc), style = MaterialTheme.typography.titleMedium)
                    toc.take(40).forEach { item ->
                        Text(
                            "${item.chapterIndex + 1}. ${item.title}" +
                                if (item.isPreview) previewSuffix else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (item.isPreview) HearPurple else AppColors.TextSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
