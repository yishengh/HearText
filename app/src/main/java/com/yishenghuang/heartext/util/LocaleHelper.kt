package com.yishenghuang.heartext.util

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.yishenghuang.heartext.data.AppLanguage
import java.util.Locale

object LocaleHelper {
    fun languageTag(language: AppLanguage): String? = when (language) {
        AppLanguage.SYSTEM -> null
        AppLanguage.ZH -> "zh-CN"
        AppLanguage.EN -> "en"
        AppLanguage.FR -> "fr"
        AppLanguage.ES -> "es"
    }

    private fun locales(language: AppLanguage): LocaleList = languageTag(language)?.let {
        LocaleList(Locale.forLanguageTag(it))
    } ?: android.content.res.Resources.getSystem().configuration.locales

    private fun resourceContext(context: Context, language: AppLanguage): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocales(locales(language))
        return context.createConfigurationContext(config)
    }

    /** Resolve against current preferences, including from a long-lived wrapped Application. */
    fun currentContext(context: Context): Context {
        val raw = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("app_language", AppLanguage.SYSTEM.name)
        val language = runCatching { AppLanguage.valueOf(raw.orEmpty()) }.getOrDefault(AppLanguage.SYSTEM)
        return resourceContext(context, language)
    }

    fun wrap(context: Context, language: AppLanguage): Context {
        locales(language).get(0)?.let(Locale::setDefault)
        return resourceContext(context, language)
    }

    fun apply(language: AppLanguage) {
        val locales = when (val tag = languageTag(language)) {
            null -> LocaleListCompat.getEmptyLocaleList()
            else -> LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun isChineseUi(language: AppLanguage): Boolean = when (language) {
        AppLanguage.ZH -> true
        AppLanguage.EN, AppLanguage.FR, AppLanguage.ES -> false
        AppLanguage.SYSTEM -> android.content.res.Resources.getSystem()
            .configuration.locales.get(0)?.language?.startsWith("zh") == true
    }
}

fun Context.localizedString(@androidx.annotation.StringRes id: Int, vararg args: Any): String =
    LocaleHelper.currentContext(this).getString(id, *args)
