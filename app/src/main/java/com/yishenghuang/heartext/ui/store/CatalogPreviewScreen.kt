package com.yishenghuang.heartext.ui.store

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yishenghuang.heartext.CatalogPreviewViewModel
import com.yishenghuang.heartext.R
import com.yishenghuang.heartext.ui.theme.AppColors
import com.yishenghuang.heartext.ui.theme.HearPurple

@Composable
fun CatalogPreviewScreen(
    viewModel: CatalogPreviewViewModel,
    onBack: () -> Unit,
    onGetFullBook: () -> Unit
) {
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val chapterIndex by viewModel.chapterIndex.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val chapter = preview?.chapters?.getOrNull(chapterIndex)

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
            Text(
                preview?.book?.title ?: stringResource(R.string.catalog_preview),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onGetFullBook) {
                Text(stringResource(R.string.catalog_download_full))
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = HearPurple)
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: stringResource(R.string.common_error))
            }
            chapter == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.catalog_no_preview))
            }
            else -> {
                val chapters = preview?.chapters.orEmpty()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { viewModel.selectChapter(chapterIndex - 1) },
                        enabled = chapterIndex > 0
                    ) { Text(stringResource(R.string.catalog_prev_chapter)) }
                    Text(
                        chapter.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall
                    )
                    TextButton(
                        onClick = { viewModel.selectChapter(chapterIndex + 1) },
                        enabled = chapterIndex < chapters.lastIndex
                    ) { Text(stringResource(R.string.catalog_next_chapter)) }
                }
                Text(
                    text = chapter.contentText,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                        .padding(bottom = 48.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
