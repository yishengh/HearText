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

    /** Wrap Activity/Application context so resource lookups use the chosen locale. */
    fun wrap(context: Context, language: AppLanguage): Context {
        val tag = languageTag(language) ?: return context
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config)
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
        AppLanguage.SYSTEM -> {
            val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            if (tag.isNotBlank()) {
                tag.startsWith("zh")
            } else {
                Locale.getDefault().language.startsWith("zh")
            }
        }
    }
}
