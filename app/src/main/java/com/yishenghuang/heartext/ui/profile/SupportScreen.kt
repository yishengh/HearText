package com.yishenghuang.heartext.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yishenghuang.heartext.R
import com.yishenghuang.heartext.ui.theme.AppColors
import com.yishenghuang.heartext.util.LegalLinks

@Composable
fun SupportScreen(
    onBack: () -> Unit,
    onOpenFeedback: () -> Unit
) {
    val context = LocalContext.current

    SettingsSubpageScaffold(title = stringResource(R.string.support_title), onBack = onBack) {
        Text(
            stringResource(R.string.support_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
        ) {
            SupportRow(
                icon = Icons.Outlined.ChatBubbleOutline,
                title = stringResource(R.string.support_feedback),
                subtitle = stringResource(R.string.support_feedback_sub),
                showDivider = true,
                onClick = onOpenFeedback
            )
            SupportRow(
                icon = Icons.Outlined.Policy,
                title = stringResource(R.string.privacy_policy),
                subtitle = stringResource(R.string.privacy_policy_sub),
                showDivider = true,
                onClick = { LegalLinks.openPrivacyPolicy(context) }
            )
            SupportRow(
                icon = Icons.Outlined.StarOutline,
                title = stringResource(R.string.support_dev),
                subtitle = stringResource(R.string.support_dev_sub),
                showDivider = false,
                onClick = {
                    val packageName = context.packageName
                    val market = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=$packageName")
                    ).apply {
                        setPackage("com.android.vending")
                    }
                    val web = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                    )
                    runCatching { context.startActivity(market) }
                        .recoverCatching { context.startActivity(web) }
                }
            )
        }

        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(16.dp)
        ) {
            Text(stringResource(R.string.support_faq), fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            FaqItem(stringResource(R.string.support_faq_import_q), stringResource(R.string.support_faq_import_a))
            FaqItem(stringResource(R.string.support_faq_audio_q), stringResource(R.string.support_faq_audio_a))
            FaqItem(stringResource(R.string.support_faq_sync_q), stringResource(R.string.support_faq_sync_a))
        }
    }
}

@Composable
private fun FaqItem(q: String, a: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(q, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
        Spacer(Modifier.height(2.dp))
        Text(a, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
    }
}

@Composable
private fun SupportRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    showDivider: Boolean,
    onClick: () -> Unit,
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
                    } else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Color(0xFF3A3A3C), modifier = Modifier.padding(end = 14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = AppColors.TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            }
            if (enabled) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    null,
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
