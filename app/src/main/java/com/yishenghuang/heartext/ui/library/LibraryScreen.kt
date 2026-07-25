package com.yishenghuang.heartext.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yishenghuang.heartext.LibraryViewModel
import com.yishenghuang.heartext.R
import com.yishenghuang.heartext.data.BookEntity
import com.yishenghuang.heartext.data.BookSource
import com.yishenghuang.heartext.ui.animation.cardPressEffect
import com.yishenghuang.heartext.ui.components.BookCover
import com.yishenghuang.heartext.ui.theme.AppColors
import com.yishenghuang.heartext.ui.theme.AppRadius
import com.yishenghuang.heartext.ui.theme.AppSpace
import com.yishenghuang.heartext.ui.theme.AppType
import com.yishenghuang.heartext.ui.theme.HearPurple
import com.yishenghuang.heartext.ui.theme.MainTabBarInset

private enum class LibraryFilter {
    ALL, READING, STORE, LOCAL
}

@Composable
private fun LibraryFilter.label(): String = when (this) {
    LibraryFilter.ALL -> stringResource(R.string.library_filter_all)
    LibraryFilter.READING -> stringResource(R.string.library_filter_reading)
    LibraryFilter.STORE -> stringResource(R.string.library_filter_store)
    LibraryFilter.LOCAL -> stringResource(R.string.library_filter_local)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenBook: (String) -> Unit,
    onContinue: (bookId: String, title: String, coverPath: String?) -> Unit
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var contextBook by remember { mutableStateOf<BookEntity?>(null) }
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }
    val haptic = LocalHapticFeedback.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importBook(uri)
    }

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val filteredBooks = remember(books, filter) {
        books.filter { book ->
            when (filter) {
                LibraryFilter.ALL -> true
                LibraryFilter.READING -> book.progressPercent > 0f && book.progressPercent < 100f
                LibraryFilter.STORE -> book.source == BookSource.CATALOG
                LibraryFilter.LOCAL -> book.source == BookSource.LOCAL
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpace.lg)
                    .padding(top = AppSpace.md, bottom = AppSpace.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.library_title),
                        fontSize = AppType.Display,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        stringResource(
                            R.string.library_subtitle,
                            filteredBooks.size,
                            books.size
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextSecondary
                    )
                }
                IconButton(
                    onClick = {
                        picker.launch(
                            arrayOf(
                                "application/epub+zip",
                                "application/pdf",
                                "text/plain",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(HearPurple.copy(alpha = 0.12f))
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.library_add_book),
                        tint = HearPurple
                    )
                }
            }

            LibraryFilterTabs(
                selected = filter,
                onSelect = { filter = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = AppSpace.md)
            )

            if (books.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = AppSpace.lg),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.library_empty_title),
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.TextPrimary
                        )
                        Spacer(Modifier.height(AppSpace.sm))
                        Text(
                            stringResource(R.string.library_empty_body),
                            color = AppColors.TextSecondary
                        )
                        Spacer(Modifier.height(AppSpace.lg))
                        TextButton(onClick = {
                            picker.launch(arrayOf("application/epub+zip", "application/pdf", "text/plain", "*/*"))
                        }) {
                            Text(stringResource(R.string.library_import_book), color = HearPurple)
                        }
                    }
                }
            } else if (filteredBooks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = AppSpace.lg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.library_filter_empty, filter.label()),
                        color = AppColors.TextSecondary
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = AppSpace.lg,
                        end = AppSpace.lg,
                        bottom = MainTabBarInset
                    ),
                    horizontalArrangement = Arrangement.spacedBy(AppSpace.md),
                    verticalArrangement = Arrangement.spacedBy(AppSpace.lg)
                ) {
                    items(filteredBooks, key = { it.id }) { book ->
                        BookGridItem(
                            book = book,
                            onClick = { onOpenBook(book.id) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                contextBook = book
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
                .padding(bottom = MainTabBarInset)
        )
    }

    contextBook?.let { book ->
        AlertDialog(
            onDismissRequest = { contextBook = null },
            title = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = { Text(stringResource(R.string.library_book_actions)) },
            confirmButton = {
                TextButton(onClick = {
                    contextBook = null
                    onContinue(book.id, book.title, book.coverPath)
                }) {
                    Text(stringResource(R.string.continue_reading), color = HearPurple)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        contextBook = null
                        onOpenBook(book.id)
                    }) {
                        Text(stringResource(R.string.action_details))
                    }
                    TextButton(onClick = {
                        viewModel.deleteBook(book.id)
                        contextBook = null
                    }) {
                        Text(stringResource(R.string.action_delete), color = AppColors.AccentCoral)
                    }
                }
            }
        )
    }
}

@Composable
private fun LibraryFilterTabs(
    selected: LibraryFilter,
    onSelect: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AppSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(AppSpace.sm)
    ) {
        LibraryFilter.entries.forEach { tab ->
            val on = selected == tab
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppRadius.full))
                    .background(if (on) HearPurple else AppColors.BgGray)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onSelect(tab) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = tab.label(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (on) androidx.compose.ui.graphics.Color.White else AppColors.TextPrimary
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookGridItem(
    book: BookEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .cardPressEffect()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        BookCover(
            book = book,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
        )
        Spacer(Modifier.height(AppSpace.sm))
        Text(
            book.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = AppColors.TextPrimary
        )
        Text(
            book.author,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = AppColors.TextSecondary
        )
        if (book.progressPercent > 0f) {
            Text(
                if (book.progressPercent >= 99.5f) "100%" else "${book.progressPercent.toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = HearPurple
            )
        }
    }
}

@Composable
fun TagChip(text: String) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(HearPurple.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = HearPurple)
    }
}
