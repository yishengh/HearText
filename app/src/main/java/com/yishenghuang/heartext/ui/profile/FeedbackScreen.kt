package com.yishenghuang.heartext.ui.profile

import android.os.Build
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yishenghuang.heartext.BuildConfig
import com.yishenghuang.heartext.HearTextApp
import com.yishenghuang.heartext.R
import com.yishenghuang.heartext.network.ApiHttpException
import com.yishenghuang.heartext.ui.theme.AppColors
import com.yishenghuang.heartext.ui.theme.HearPurple
import kotlinx.coroutines.launch

@Composable
fun FeedbackScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HearTextApp
    val api = app.container.api
    val scope = rememberCoroutineScope()

    var message by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusOk by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = HearPurple,
        cursorColor = HearPurple,
        focusedLabelColor = HearPurple
    )

    val tooShort = stringResource(R.string.feedback_too_short)
    val notConfigured = stringResource(R.string.feedback_not_configured)
    val okMsg = stringResource(R.string.feedback_ok)
    val comingSoon = stringResource(R.string.feedback_coming_soon)
    val failGeneric = stringResource(R.string.feedback_fail)

    SettingsSubpageScaffold(title = stringResource(R.string.feedback_title), onBack = onBack) {
        Text(
            stringResource(R.string.feedback_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = message,
            onValueChange = {
                if (it.length <= 2000) message = it
                status = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            label = { Text(stringResource(R.string.feedback_content)) },
            placeholder = { Text(stringResource(R.string.feedback_content_hint)) },
            enabled = !submitting,
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = contact,
            onValueChange = {
                if (it.length <= 120) contact = it
                status = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.feedback_contact)) },
            placeholder = { Text(stringResource(R.string.feedback_contact_hint)) },
            singleLine = true,
            enabled = !submitting,
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.feedback_device_note),
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextSecondary
        )

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                val body = message.trim()
                if (body.length < 5) {
                    statusOk = false
                    status = tooShort
                    return@Button
                }
                if (!api.isConfigured) {
                    statusOk = false
                    status = notConfigured
                    return@Button
                }
                submitting = true
                status = null
                scope.launch {
                    val deviceInfo = buildString {
                        append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                        append("; Android ").append(Build.VERSION.RELEASE)
                        append(" (API ").append(Build.VERSION.SDK_INT).append(')')
                    }
                    runCatching {
                        api.submitFeedback(
                            message = body,
                            contact = contact.trim().ifBlank { null },
                            appVersion = BuildConfig.VERSION_NAME,
                            appVersionCode = BuildConfig.VERSION_CODE,
                            deviceInfo = deviceInfo
                        )
                    }.onSuccess {
                        statusOk = true
                        status = okMsg
                        message = ""
                        contact = ""
                    }.onFailure { e ->
                        statusOk = false
                        status = when (e) {
                            is ApiHttpException -> when (e.code) {
                                404, 501 -> comingSoon
                                else -> context.getString(R.string.feedback_fail_code, e.code)
                            }
                            else -> e.message?.take(80) ?: failGeneric
                        }
                    }
                    submitting = false
                }
            },
            enabled = !submitting,
            colors = ButtonDefaults.buttonColors(containerColor = HearPurple),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                if (submitting) stringResource(R.string.feedback_submitting)
                else stringResource(R.string.feedback_submit)
            )
        }

        status?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                it,
                color = if (statusOk) HearPurple else Color(0xFFE85D5D),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
