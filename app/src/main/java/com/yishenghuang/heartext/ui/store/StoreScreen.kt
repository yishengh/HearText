package com.yishenghuang.heartext.ui.store

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import android.content.Context
import com.yishenghuang.heartext.R
import com.yishenghuang.heartext.StoreViewModel
import com.yishenghuang.heartext.network.ApiCatalogBook
import com.yishenghuang.heartext.network.ApiCatalogCategory
import com.yishenghuang.heartext.ui.theme.AppColors
import com.yishenghuang.heartext.ui.theme.AppRadius
import com.yishenghuang.heartext.ui.theme.AppSpace
import com.yishenghuang.heartext.ui.theme.HearPurple
import com.yishenghuang.heartext.ui.theme.MainTabBarInset
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun StoreScreen(
    viewModel: StoreViewModel,
    onOpenCatalog: (String) -> Unit,
    onRequestSignIn: () -> Unit
) {
    val featured by viewModel.featured.collectAsStateWithLifecycle()
    val rankings by viewModel.rankings.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val resultsTotal by viewModel.resultsTotal.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val loadingMore by viewModel.loadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val browsing = query.isBlank() && selectedCategory == null
    val listState = rememberLazyListState()

    LaunchedEffect(listState, browsing, hasMore, loadingMore) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            last >= total - 3 && total > 0
        }
            .distinctUntilChanged()
            .filter { it && !browsing && hasMore && !loadingMore }
            .collect { viewModel.loadMore() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
            .statusBarsPadding()
    ) {
        Text(
            stringResource(R.string.store_title),
            fontSize = 28.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary,
            modifier = Modifier.padding(horizontal = AppSpace.lg, vertical = AppSpace.md)
        )

        StoreSearchBar(
            query = query,
            onQueryChange = viewModel::setQuery,
            onSearch = {
                focusManager.clearFocus()
                viewModel.submitSearch()
            },
            onClear = {
                viewModel.setQuery("")
                viewModel.submitSearch()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpace.lg)
        )

        if (categories.isNotEmpty()) {
            Spacer(Modifier.height(AppSpace.md))
            StoreCategoryRow(
                categories = categories,
                selectedSlug = selectedCategory,
                onSelect = viewModel::selectCategory
            )
        }

        Spacer(Modifier.height(AppSpace.sm))

        when {
            loading && featured.isEmpty() && results.isEmpty() && rankings.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = HearPurple)
                }
            }
            error != null && featured.isEmpty() && rankings.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(AppSpace.lg),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(error ?: stringResource(R.string.store_load_failed), color = AppColors.TextSecondary)
                    Spacer(Modifier.height(AppSpace.md))
                    TextButton(onClick = onRequestSignIn) {
                        Text(stringResource(R.string.store_sign_in_browse))
                    }
                    TextButton(onClick = viewModel::refresh) {
                        Text(stringResource(R.string.store_retry))
                    }
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = AppSpace.sm, bottom = MainTabBarInset)
                ) {
                    if (!browsing) {
                        item {
                            val cat = categories.firstOrNull { it.slug == selectedCategory }
                            val context = LocalContext.current
                            SectionHeader(
                                title = if (selectedCategory != null && query.isBlank()) {
                                    categoryLabel(context, cat)
                                } else {
                                    stringResource(R.string.store_results)
                                },
                                trailing = if (resultsTotal > 0) {
                                    stringResource(R.string.store_books_count, resultsTotal)
                                } else if (results.isNotEmpty()) {
                                    stringResource(R.string.store_books_count, results.size)
                                } else null
                            )
                        }
                        if (results.isEmpty() && !loading) {
                            item {
                                EmptyHint(
                                    if (query.isNotBlank()) stringResource(R.string.store_no_results)
                                    else stringResource(R.string.store_category_empty)
                                )
                            }
                        } else {
                            items(results, key = { it.id }) { book ->
                                CatalogBookRow(book) { onOpenCatalog(book.id) }
                            }
                            if (loadingMore) {
                                item {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(AppSpace.md),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = HearPurple,
                                            modifier = Modifier.size(28.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        if (featured.isNotEmpty()) {
                            item { SectionHeader(stringResource(R.string.store_featured)) }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = AppSpace.lg),
                                    horizontalArrangement = Arrangement.spacedBy(AppSpace.md)
                                ) {
                                    items(featured, key = { it.id }) { book ->
                                        CatalogCoverCard(book) { onOpenCatalog(book.id) }
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(AppSpace.md)) }
                        }
                        if (rankings.isNotEmpty()) {
                            item { SectionHeader(stringResource(R.string.store_rankings)) }
                            itemsIndexed(
                                rankings,
                                key = { _, book -> "r-${book.id}" }
                            ) { index, book ->
                                CatalogRankRow(index + 1, book) { onOpenCatalog(book.id) }
                            }
                        }
                        if (results.isNotEmpty() && featured.isEmpty() && rankings.isEmpty()) {
                            item { SectionHeader(stringResource(R.string.store_all)) }
                            items(results, key = { it.id }) { book ->
                                CatalogBookRow(book) { onOpenCatalog(book.id) }
                            }
                            if (loadingMore) {
                                item {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(AppSpace.md),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = HearPurple,
                                            modifier = Modifier.size(28.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(AppRadius.lg))
            .background(AppColors.BgGray)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = AppColors.TextPrimary),
            cursorBrush = SolidColor(HearPurple),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.store_search_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppColors.TextSecondary.copy(alpha = 0.7f)
                        )
                    }
                    inner()
                }
            }
        )
        if (query.isNotEmpty()) {
            Icon(
                Icons.Default.Clear,
                contentDescription = stringResource(R.string.store_clear),
                tint = AppColors.TextSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onClear
                    )
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            stringResource(R.string.store_search),
            color = HearPurple,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onSearch
            )
        )
    }
}

@Composable
private fun StoreCategoryRow(
    categories: List<ApiCatalogCategory>,
    selectedSlug: String?,
    onSelect: (String?) -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AppSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(AppSpace.sm)
    ) {
        CategoryChip(stringResource(R.string.store_all), selectedSlug == null) { onSelect(null) }
        categories.forEach { cat ->
            CategoryChip(categoryLabel(context, cat), selectedSlug == cat.slug) { onSelect(cat.slug) }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(AppRadius.full))
            .background(if (selected) HearPurple else Color.Transparent)
            .then(
                if (!selected) Modifier.border(1.dp, AppColors.Divider, RoundedCornerShape(AppRadius.full))
                else Modifier
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) Color.White else AppColors.TextPrimary
        )
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.lg)
            .padding(top = AppSpace.sm, bottom = AppSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        if (!trailing.isNullOrBlank()) {
            Text(trailing, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun CatalogCoverCard(book: ApiCatalogBook, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .size(112.dp, 154.dp)
                .clip(RoundedCornerShape(AppRadius.md))
                .background(HearPurple.copy(alpha = 0.10f))
        ) {
            if (!book.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = book.coverUrl,
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            book.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = AppColors.TextPrimary
        )
        Text(
            book.author ?: "",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextSecondary
        )
    }
}

@Composable
fun CatalogBookRow(book: ApiCatalogBook, onClick: () -> Unit) {
    CatalogRankRow(rank = null, book = book, onClick = onClick)
}

@Composable
private fun CatalogRankRow(rank: Int?, book: ApiCatalogBook, onClick: () -> Unit) {
    val secondary = AppColors.TextSecondary
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(horizontal = AppSpace.lg, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rank != null) {
            val rankColor = when (rank) {
                1 -> Color(0xFFE6A817)
                2 -> Color(0xFF9AA0A6)
                3 -> Color(0xFFCD7F32)
                else -> secondary
            }
            Text(
                text = rank.toString(),
                modifier = Modifier.width(28.dp),
                color = rankColor,
                fontSize = if (rank <= 3) 18.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        Box(
            modifier = Modifier
                .size(60.dp, 84.dp)
                .clip(RoundedCornerShape(AppRadius.sm))
                .background(HearPurple.copy(alpha = 0.10f))
        ) {
            if (!book.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = book.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                book.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                book.author ?: stringResource(R.string.store_unknown_author),
                color = secondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val tags = book.categories.take(3).joinToString(" · ") { categoryLabel(context, it) }
                .ifBlank { book.language.uppercase() }
            if (tags.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    tags,
                    color = secondary.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun categoryLabel(context: Context, category: ApiCatalogCategory?): String {
    if (category == null) return context.getString(R.string.category_fallback)
    val key = category.slug.lowercase().ifBlank { category.name.lowercase() }
    val resId = categoryStringId(key)
    return if (resId != 0) context.getString(resId) else category.name
}

private fun categoryStringId(key: String): Int = when (key) {
    "adventure" -> R.string.category_adventure
    "classics" -> R.string.category_classics
    "fantasy" -> R.string.category_fantasy
    "fiction" -> R.string.category_fiction
    "horror" -> R.string.category_horror
    "humor" -> R.string.category_humor
    "mystery" -> R.string.category_mystery
    "romance" -> R.string.category_romance
    "sci-fi", "science-fiction", "science fiction" -> R.string.category_scifi
    "poetry" -> R.string.category_poetry
    "drama" -> R.string.category_drama
    "history" -> R.string.category_history
    "philosophy" -> R.string.category_philosophy
    "biography" -> R.string.category_biography
    "children" -> R.string.category_children
    "short-stories", "short stories" -> R.string.category_short_stories
    else -> 0
}