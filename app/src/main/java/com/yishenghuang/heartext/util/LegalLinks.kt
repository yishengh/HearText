package com.yishenghuang.heartext.util

import android.content.Context
import android.content.Intent
import android.net.Uri

object LegalLinks {
    const val PRIVACY_POLICY_URL = "https://heartext-privacy.netlify.app"

    fun openPrivacyPolicy(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))
        runCatching { context.startActivity(intent) }
    }
}
