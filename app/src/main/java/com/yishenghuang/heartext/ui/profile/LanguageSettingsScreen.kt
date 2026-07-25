package com.yishenghuang.heartext.ui.profile

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yishenghuang.heartext.HearTextApp
import com.yishenghuang.heartext.R
import com.yishenghuang.heartext.data.AppLanguage
import com.yishenghuang.heartext.data.AppPreferences
import com.yishenghuang.heartext.ui.theme.AppColors
import com.yishenghuang.heartext.ui.theme.HearPurple
import com.yishenghuang.heartext.util.LocaleHelper

private fun applyLanguage(activity: Activity?, prefs: AppPreferences, language: AppLanguage) {
    prefs.setLanguage(language)
    LocaleHelper.apply(language)
    activity?.recreate()
}

@Composable
fun LanguageSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = (context.applicationContext as HearTextApp).container.appPreferences
    val language by prefs.language.collectAsStateWithLifecycle()

    SettingsSubpageScaffold(title = stringResource(R.string.language_title), onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
        ) {
            LanguageOption(
                title = stringResource(R.string.language_system),
                selected = language == AppLanguage.SYSTEM,
                showDivider = true,
                onClick = { applyLanguage(activity, prefs, AppLanguage.SYSTEM) }
            )
            LanguageOption(
                title = stringResource(R.string.language_zh),
                selected = language == AppLanguage.ZH,
                showDivider = true,
                onClick = { applyLanguage(activity, prefs, AppLanguage.ZH) }
            )
            LanguageOption(
                title = stringResource(R.string.language_en),
                selected = language == AppLanguage.EN,
                showDivider = true,
                onClick = { applyLanguage(activity, prefs, AppLanguage.EN) }
            )
            LanguageOption(
                title = stringResource(R.string.language_fr),
                selected = language == AppLanguage.FR,
                showDivider = true,
                onClick = { applyLanguage(activity, prefs, AppLanguage.FR) }
            )
            LanguageOption(
                title = stringResource(R.string.language_es),
                selected = language == AppLanguage.ES,
                showDivider = false,
                onClick = { applyLanguage(activity, prefs, AppLanguage.ES) }
            )
        }
    }
}

@Composable
private fun LanguageOption(
    title: String,
    selected: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = AppColors.TextPrimary
            )
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = HearPurple,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Spacer(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, AppColors.Divider, CircleShape)
                )
            }
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
