package com.yishenghuang.heartext.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yishenghuang.heartext.HearTextApp
import com.yishenghuang.heartext.ProfileViewModel
import com.yishenghuang.heartext.R
import com.yishenghuang.heartext.data.AppLanguage
import com.yishenghuang.heartext.data.LibraryStats
import com.yishenghuang.heartext.data.TtsVoiceSource
import com.yishenghuang.heartext.network.ApiUser
import com.yishenghuang.heartext.ui.theme.AppColors
import com.yishenghuang.heartext.ui.theme.AppSpace
import com.yishenghuang.heartext.ui.theme.HearPurple
import com.yishenghuang.heartext.ui.theme.MainTabBarInset

private val SettingsCardShape = RoundedCornerShape(16.dp)
private val SettingsIconTint = Color(0xFF3A3A3C)
private val SettingsPageBg = Color(0xFFF2F2F7)

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    authSignedIn: Boolean,
    onSignOut: () -> Unit,
    onRequestSignIn: () -> Unit,
    onOpenListenSettings: () -> Unit,
    onOpenTypography: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenLanguage: () -> Unit
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val stats by viewModel.libraryStats.collectAsStateWithLifecycle()
    val readerSettings by viewModel.settings.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var editingName by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(user?.displayName) {
        displayName = user?.displayName.orEmpty()
    }

    val voiceSummary = when (readerSettings.ttsVoiceSource) {
        TtsVoiceSource.OFFLINE -> stringResource(R.string.voice_summary_offline)
        else -> stringResource(R.string.voice_summary_system)
    }
    val appPrefs = (LocalContext.current.applicationContext as HearTextApp).container.appPreferences
    val appLanguage by appPrefs.language.collectAsStateWithLifecycle()
    val languageSubtitle = when (appLanguage) {
        AppLanguage.SYSTEM -> stringResource(R.string.language_current_system)
        AppLanguage.ZH -> stringResource(R.string.language_current_zh)
        AppLanguage.EN -> stringResource(R.string.language_current_en)
        AppLanguage.FR -> stringResource(R.string.language_current_fr)
        AppLanguage.ES -> stringResource(R.string.language_current_es)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SettingsPageBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = MainTabBarInset)
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_title),
            fontSize = 34.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))

        SettingsCard {
            if (!authSignedIn) {
                ProfileRow(
                    name = stringResource(R.string.settings_guest),
                    subtitle = stringResource(R.string.settings_tap_sign_in),
                    onClick = onRequestSignIn
                )
            } else {
                ProfileRow(
                    name = user.displayNameOrFallback(),
                    subtitle = stringResource(R.string.settings_edit_name),
                    onClick = {
                        displayName = user?.displayName.orEmpty()
                        editingName = true
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsCard {
            StatsStrip(stats)
        }

        Spacer(Modifier.height(16.dp))

        SettingsCard {
            SettingsListRow(
                icon = Icons.Outlined.Language,
                title = stringResource(R.string.settings_language),
                subtitle = languageSubtitle,
                onClick = onOpenLanguage,
                showDivider = true
            )
            SettingsListRow(
                icon = Icons.Outlined.TextFields,
                title = stringResource(R.string.settings_typography),
                subtitle = stringResource(R.string.settings_typography_sub),
                onClick = onOpenTypography,
                showDivider = true
            )
            SettingsListRow(
                icon = Icons.Outlined.Headphones,
                title = stringResource(R.string.settings_listen),
                subtitle = voiceSummary,
                onClick = onOpenListenSettings,
                showDivider = true
            )
            SettingsListRow(
                icon = Icons.Outlined.FolderOpen,
                title = stringResource(R.string.settings_storage),
                subtitle = stringResource(R.string.settings_storage_sub),
                onClick = onOpenStorage,
                showDivider = true
            )
            SettingsListRow(
                icon = Icons.Outlined.SupportAgent,
                title = stringResource(R.string.settings_support),
                subtitle = stringResource(R.string.settings_support_sub),
                onClick = onOpenSupport,
                showDivider = true
            )
            SettingsListRow(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.settings_about),
                onClick = onOpenAbout,
                showDivider = authSignedIn
            )
            if (authSignedIn) {
                SettingsListRow(
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    title = stringResource(R.string.settings_sign_out),
                    onClick = onSignOut,
                    showDivider = true
                )
                SettingsListRow(
                    icon = Icons.Outlined.DeleteOutline,
                    title = stringResource(R.string.settings_delete_account),
                    titleColor = Color(0xFFE85D5D),
                    onClick = { confirmDelete = true },
                    showDivider = false
                )
            }
        }

        message?.let {
            Spacer(Modifier.height(AppSpace.md))
            Text(it, color = HearPurple, style = MaterialTheme.typography.bodyMedium)
            LaunchedEffect(it) {
                kotlinx.coroutines.delay(3200)
                viewModel.clearMessage()
            }
        }
    }

    if (editingName) {
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text(stringResource(R.string.profile_edit_name_title)) },
            text = {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(stringResource(R.string.profile_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateDisplayName(displayName)
                    editingName = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editingName = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.profile_delete_title)) },
            text = { Text(stringResource(R.string.profile_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteAccount()
                    onSignOut()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun ApiUser?.displayNameOrFallback(): String {
    val fallback = stringResource(R.string.profile_default_name)
    return this?.displayName?.takeIf { it.isNotBlank() }
        ?: this?.username?.takeIf { it.isNotBlank() }
        ?: fallback
}

@Composable
private fun ProfileRow(
    name: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(0xFFE8E8ED)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Person,
                contentDescription = null,
                tint = Color(0xFF8E8E93),
                modifier = Modifier.size(28.dp)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        ) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFFC7C7CC)
        )
    }
}

@Composable
private fun StatsStrip(stats: LibraryStats) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatCell(value = "${stats.totalBooks}", label = stringResource(R.string.profile_stat_shelf))
        StatCell(
            value = stats.formatReadingTime(context),
            label = stringResource(R.string.profile_stat_time)
        )
        StatCell(value = "${stats.readingBooks}", label = stringResource(R.string.profile_stat_reading))
        StatCell(value = "${stats.finishedBooks}", label = stringResource(R.string.profile_stat_finished))
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.TextSecondary
        )
    }
}

@Composable
private fun SettingsListRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    titleColor: Color = AppColors.TextPrimary,
    onClick: () -> Unit,
    showDivider: Boolean,
    enabled: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (enabled) {
                        Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onClick
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (titleColor == AppColors.TextPrimary) SettingsIconTint else titleColor,
                modifier = Modifier.size(22.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary
                    )
                }
            }
            if (enabled) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFFC7C7CC)
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 52.dp),
                thickness = 0.5.dp,
                color = Color(0xFFE5E5EA)
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SettingsCardShape)
            .background(Color.White)
    ) {
        content()
    }
}
