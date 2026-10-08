package com.yishenghuang.heartext.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLanguage {
    SYSTEM,
    ZH,
    EN,
    FR,
    ES
}

class AppPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private val _language = MutableStateFlow(readLanguage())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private fun readLanguage(): AppLanguage {
        val raw = prefs.getString("app_language", AppLanguage.SYSTEM.name) ?: AppLanguage.SYSTEM.name
        return runCatching { AppLanguage.valueOf(raw) }.getOrDefault(AppLanguage.SYSTEM)
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString("app_language", language.name).apply()
        _language.value = language
    }

    /** Whether the UI should treat itself as Chinese (for reader script toggle, etc.). */
    fun isChineseUi(): Boolean = com.yishenghuang.heartext.util.LocaleHelper.isChineseUi(_language.value)
}
