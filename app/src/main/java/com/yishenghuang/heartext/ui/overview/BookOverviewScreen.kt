package com.yishenghuang.heartext.ui.overview

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yishenghuang.heartext.BookOverviewViewModel
import com.yishenghuang.heartext.R
import com.yishenghuang.heartext.ui.animation.pressEffect
import com.yishenghuang.heartext.ui.components.BookCover
import com.yishenghuang.heartext.ui.library.TagChip
import com.yishenghuang.heartext.ui.theme.AppColors
import com.yishenghuang.heartext.ui.theme.AppRadius
import com.yishenghuang.heartext.ui.theme.AppSpace
import com.yishenghuang.heartext.ui.theme.HearPurple

@Composable
fun BookOverviewScreen(
    viewModel: BookOverviewViewModel,
    onBack: () -> Unit,
    onKeepReading: (bookId: String, title: String, coverPath: String?) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val book = (state as? com.yishenghuang.heartext.BookOverviewState.Ready)?.book
    val coverSaving by viewModel.coverSaving.collectAsStateWithLifecycle()
    val coverError by viewModel.coverError.collectAsStateWithLifecycle()
    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.changeCover(uri)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = AppSpace.sm, vertical = AppSpace.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(AppColors.BgGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = AppColors.TextPrimary
                        )
                    }
                }
            }

            val b = book
            if (b == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (state) {
                        com.yishenghuang.heartext.BookOverviewState.Loading -> CircularProgressIndicator(color = HearPurple)
                        com.yishenghuang.heartext.BookOverviewState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.error_overview_load))
                            androidx.compose.material3.TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.action_retry)) }
                        }
                        else -> Text(stringResource(R.string.error_book_not_found))
                    }
                }
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpace.lg),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .pressEffect()
                            .clickable(
                                enabled = !coverSaving,
                                onClickLabel = stringResource(R.string.overview_change_cover),
                                role = androidx.compose.ui.semantics.Role.Button,
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { coverPicker.launch("image/*") }
                    ) {
                        BookCover(book = b, modifier = Modifier.width(140.dp))
                    }
                    Spacer(Modifier.height(AppSpace.sm))
                    Text(
                        stringResource(R.string.overview_change_cover),
                        color = HearPurple,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable(enabled = !coverSaving) { coverPicker.launch("image/*") }
                    )
                    if (coverSaving) CircularProgressIndicator(Modifier.size(24.dp))
                    if (coverError) Text(stringResource(R.string.error_cover_save), color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(AppSpace.md))
                    Text(
                        b.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Text(stringResource(R.string.overview_by_author, b.author), color = AppColors.TextSecondary)
                    Spacer(Modifier.height(AppSpace.lg))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = AppRadius.xl, topEnd = AppRadius.xl))
                        .background(AppColors.CardBg)
                        .padding(AppSpace.lg)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpace.md)
                    ) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = { Icon(Icons.Default.Timer, null, tint = HearPurple) },
                            label = stringResource(R.string.overview_progress),
                            value = "${b.progressPercent.toInt()}%"
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = { Icon(Icons.Default.MenuBook, null, tint = AppColors.AccentCoral) },
                            label = stringResource(R.string.overview_chapters),
                            value = "${b.lastChapterIndex + 1}/${b.totalChapters}"
                        )
                    }
                    Spacer(Modifier.height(AppSpace.lg))
                    Text(
                        stringResource(R.string.overview_format),
                        fontWeight = FontWeight.Medium,
                        color = AppColors.TextPrimary
                    )
                    Spacer(Modifier.height(AppSpace.sm))
                    TagChip(b.format.name)
                    Spacer(Modifier.height(AppSpace.lg))
                    Text(
                        stringResource(R.string.overview_about),
                        fontWeight = FontWeight.Medium,
                        color = AppColors.TextPrimary
                    )
                    Spacer(Modifier.height(AppSpace.sm))
                    Text(
                        b.description?.takeIf { it.isNotBlank() }
                            ?: if (b.format == com.yishenghuang.heartext.data.BookFormat.PDF) {
                                stringResource(R.string.overview_about_pdf)
                            } else {
                                stringResource(R.string.overview_about_text)
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextSecondary
                    )
                    Spacer(Modifier.height(AppSpace.lg))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AppRadius.capsule))
                            .background(AppColors.TextPrimary)
                            .pressEffect()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onKeepReading(b.id, b.title, b.coverPath) }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Headphones, null, tint = AppColors.CardBg, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.keep_reading),
                                color = AppColors.CardBg,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    label: String,
    value: String
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AppRadius.lg))
            .background(AppColors.BgGray)
            .padding(AppSpace.md)
    ) {
        icon()
        Spacer(Modifier.height(AppSpace.sm))
        Text(label, style = MaterialTheme.typography.labelLarge, color = AppColors.TextSecondary)
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
    }
}
