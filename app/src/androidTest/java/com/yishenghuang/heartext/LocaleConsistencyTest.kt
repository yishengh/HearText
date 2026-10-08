package com.yishenghuang.heartext

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.yishenghuang.heartext.data.AppLanguage
import com.yishenghuang.heartext.data.AppPreferences
import com.yishenghuang.heartext.util.LocaleHelper
import com.yishenghuang.heartext.util.localizedString
import org.junit.Test
import org.junit.Assert.*
import java.util.Locale
import java.util.UUID

class LocaleConsistencyTest {
    private val labels = linkedMapOf(AppLanguage.EN to "Book not found", AppLanguage.ZH to "未找到书籍",
        AppLanguage.FR to "Livre introuvable", AppLanguage.ES to "Libro no encontrado")

    @Test fun longLivedContextUsesCurrentPreferencesAndSystemDoesNotKeepOldOverride() {
        check(BuildConfig.APPLICATION_ID.endsWith(".validation"))
        val app = ApplicationProvider.getApplicationContext<Context>()
        val name = "locale-${UUID.randomUUID()}"
        val config = Configuration(app.resources.configuration).apply { setLocales(LocaleList(Locale.ENGLISH)) }
        val stale = app.createConfigurationContext(config)
        val context = object : ContextWrapper(stale) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(ignored: String, mode: Int) = app.getSharedPreferences(name, mode)
        }
        val prefs = AppPreferences(context)
        try {
            labels.forEach { (language, expected) ->
                prefs.setLanguage(language)
                assertEquals(expected, context.localizedString(R.string.error_book_not_found))
                assertEquals("Book not found", context.getString(R.string.error_book_not_found))
            }
            prefs.setLanguage(AppLanguage.SYSTEM)
            val systemConfig = Configuration(config).apply { setLocales(Resources.getSystem().configuration.locales) }
            assertEquals(app.createConfigurationContext(systemConfig).getString(R.string.error_book_not_found),
                context.localizedString(R.string.error_book_not_found))
            assertEquals(Resources.getSystem().configuration.locales[0].language == "zh", prefs.isChineseUi())
        } finally { app.deleteSharedPreferences(name) }
    }

    @Test fun activityAndExistingApplicationAgreeAfterEachLanguageSwitch() {
        check(BuildConfig.APPLICATION_ID.endsWith(".validation"))
        val app = ApplicationProvider.getApplicationContext<HearTextApp>()
        val prefs = app.container.appPreferences
        val previous = prefs.language.value
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                labels.forEach { (language, expected) ->
                    scenario.onActivity { prefs.setLanguage(language); LocaleHelper.apply(language) }
                    val deadline = SystemClock.uptimeMillis() + 10_000
                    var matched = false
                    while (!matched && SystemClock.uptimeMillis() < deadline) {
                        scenario.onActivity { matched = it.getString(R.string.error_book_not_found) == expected }
                        if (!matched) SystemClock.sleep(30)
                    }
                    assertTrue("Activity did not switch to $language", matched)
                    assertEquals(expected, app.localizedString(R.string.error_book_not_found))
                }
            } finally {
                scenario.onActivity { prefs.setLanguage(previous); LocaleHelper.apply(previous) }
            }
        }
    }
}
