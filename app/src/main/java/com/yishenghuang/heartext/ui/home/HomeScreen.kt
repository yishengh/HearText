package com.yishenghuang.heartext.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yishenghuang.heartext.HomeViewModel
import com.yishenghuang.heartext.R
import com.yishenghuang.heartext.data.BookEntity
import com.yishenghuang.heartext.ui.animation.cardPressEffect
import com.yishenghuang.heartext.ui.animation.pressEffect
import com.yishenghuang.heartext.ui.auth.AuthUiState
import com.yishenghuang.heartext.ui.auth.AuthViewModel
import com.yishenghuang.heartext.ui.components.BookCover
import com.yishenghuang.heartext.ui.components.StatusGradientOverlay
import com.yishenghuang.heartext.ui.library.TagChip
import com.yishenghuang.heartext.ui.theme.AppColors
import com.yishenghuang.heartext.ui.theme.AppRadius
import com.yishenghuang.heartext.ui.theme.AppSpace
import com.yishenghuang.heartext.ui.theme.AppType
import com.yishenghuang.heartext.ui.theme.HearPurple
import com.yishenghuang.heartext.ui.theme.MainTabBarInset

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    authViewModel: AuthViewModel,
    onOpenBook: (String) -> Unit,
    onContinue: (bookId: String, title: String, coverPath: String?) -> Unit,
    onRequestSignIn: () -> Unit
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val meLabel by authViewModel.meLabel.collectAsStateWithLifecycle()

    val sortedBooks = remember(books) {
        books.sortedWith(
            compareByDescending<BookEntity> { it.progressPercent > 0f }
                .thenByDescending { it.progressUpdatedAt }
                .thenByDescending { it.addedAt }
        )
    }
    val continueBook = sortedBooks.firstOrNull { it.progressPercent > 0f } ?: sortedBooks.firstOrNull()
    val otherBooks = sortedBooks.filter { it.id != continueBook?.id }
    val inProgressCount = books.count { it.progressPercent > 0f }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AppSpace.lg,
                end = AppSpace.lg,
                top = AppSpace.lg,
                bottom = MainTabBarInset
            )
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.home_title),
                        fontSize = AppType.Display,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (authState is AuthUiState.SignedIn) {
                                Icons.Default.CloudDone
                            } else {
                                Icons.Default.CloudOff
                            },
                            contentDescription = null,
                            tint = if (authState is AuthUiState.SignedIn) HearPurple else AppColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        if (authState is AuthUiState.SignedIn) {
                            TextButton(onClick = { authViewModel.signOut() }) {
                                Text(stringResource(R.string.sign_out), color = AppColors.TextSecondary)
                            }
                        } else {
                            TextButton(onClick = onRequestSignIn) {
                                Text(stringResource(R.string.sign_in), color = HearPurple)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(AppSpace.sm))
                Text(
                    text = when {
                        authState is AuthUiState.SignedIn ->
                            stringResource(R.string.home_cloud_on) + (meLabel?.let { " · $it" } ?: "")
                        else -> stringResource(R.string.home_cloud_off)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )
                Spacer(Modifier.height(AppSpace.lg))
            }

            item {
                MiniStatsRow(
                    bookCount = books.size,
                    inProgressCount = inProgressCount,
                    signedIn = authState is AuthUiState.SignedIn
                )
                Spacer(Modifier.height(AppSpace.lg))
            }

            item {
                Text(
                    stringResource(R.string.continue_reading),
                    fontSize = AppType.Section,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.height(AppSpace.md))
            }

            item {
                if (continueBook == null) {
                    EmptyContinueCard(onRequestSignIn = onRequestSignIn, signedIn = authState is AuthUiState.SignedIn)
                } else {
                    ContinueReadingCard(
                        book = continueBook,
                        onOpen = { onOpenBook(continueBook.id) },
                        onContinue = {
                            onContinue(continueBook.id, continueBook.title, continueBook.coverPath)
                        }
                    )
                }
                Spacer(Modifier.height(AppSpace.xl))
            }

            if (otherBooks.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.home_read_before),
                        fontSize = AppType.Section,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary
                    )
                    Spacer(Modifier.height(AppSpace.md))
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(AppSpace.md)) {
                        items(otherBooks.take(8), key = { it.id }) { book ->
                            ReadBeforeCard(
                                book = book,
                                onClick = { onOpenBook(book.id) }
                            )
                        }
                    }
                    Spacer(Modifier.height(AppSpace.lg))
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.home_listen_title),
                        fontSize = AppType.Section,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary
                    )
                    Icon(Icons.Default.Headphones, null, tint = HearPurple, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(AppSpace.sm))
                Text(
                    stringResource(R.string.home_listen_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary
                )
            }
        }

        StatusGradientOverlay(modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun MiniStatsRow(
    bookCount: Int,
    inProgressCount: Int,
    signedIn: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpace.sm)
    ) {
        MiniStatChip(
            label = stringResource(R.string.home_stat_books),
            value = bookCount.toString(),
            modifier = Modifier.weight(1f)
        )
        MiniStatChip(
            label = stringResource(R.string.home_stat_reading),
            value = inProgressCount.toString(),
            modifier = Modifier.weight(1f)
        )
        MiniStatChip(
            label = stringResource(R.string.home_stat_sync),
            value = if (signedIn) stringResource(R.string.common_on) else stringResource(R.string.common_off),
            modifier = Modifier.weight(1f),
            highlight = signedIn
        )
    }
}

@Composable
private fun MiniStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AppRadius.lg))
            .background(AppColors.CardBg)
            .padding(horizontal = AppSpace.md, vertical = AppSpace.md)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (highlight) HearPurple else AppColors.TextPrimary
        )
    }
}

@Composable
private fun EmptyContinueCard(onRequestSignIn: () -> Unit, signedIn: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.xl))
            .background(AppColors.CardBg)
            .padding(AppSpace.lg)
    ) {
        Icon(Icons.Default.MenuBook, null, tint = HearPurple.copy(alpha = 0.5f), modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(AppSpace.sm))
        Text(
            stringResource(R.string.library_empty_title),
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary
        )
        Text(
            stringResource(R.string.library_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary
        )
        if (!signedIn) {
            Spacer(Modifier.height(AppSpace.sm))
            TextButton(onClick = onRequestSignIn) {
                Text(stringResource(R.string.home_empty_sync), color = HearPurple)
            }
        }
    }
}

@Composable
private fun ContinueReadingCard(
    book: BookEntity,
    onOpen: () -> Unit,
    onContinue: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.xl))
            .background(AppColors.CardBg)
            .cardPressEffect()
            .clickable(onClick = onOpen)
            .padding(AppSpace.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookCover(book = book, modifier = Modifier.width(88.dp))
        Spacer(Modifier.width(AppSpace.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                book.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = AppColors.TextPrimary
            )
            Text(book.author, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
            Spacer(Modifier.height(6.dp))
            Text(
                "${book.format.name} · ${book.progressPercent.toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = HearPurple
            )
        }
    }
    Spacer(Modifier.height(AppSpace.sm))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.capsule))
            .background(AppColors.TextPrimary)
            .pressEffect()
            .clickable(onClick = onContinue)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(R.string.home_continue_book, book.title),
            color = AppColors.CardBg,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = AppSpace.lg)
        )
    }
}

@Composable
private fun ReadBeforeCard(book: BookEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .width(260.dp)
            .clip(RoundedCornerShape(AppRadius.lg))
            .background(AppColors.CardBg)
            .cardPressEffect()
            .clickable(onClick = onClick)
            .padding(AppSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookCover(book = book, modifier = Modifier.width(52.dp))
        Spacer(Modifier.width(AppSpace.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = AppColors.TextPrimary
            )
            Text(book.author, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            if (book.progressPercent > 0f) {
                TagChip("${book.progressPercent.toInt()}%")
            }
        }
    }
}
