package com.yishenghuang.heartext.readium

import com.yishenghuang.heartext.data.PageTurnEffect
import com.yishenghuang.heartext.data.ReaderFontFamily
import com.yishenghuang.heartext.data.ReaderSettings
import com.yishenghuang.heartext.data.ReaderThemeMode
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
object EpubPreferenceMapper {

    fun from(settings: ReaderSettings): EpubPreferences {
        val scroll = false
        val publisherStyles = settings.fontFamily == ReaderFontFamily.PUBLISHER &&
            settings.letterSpacing == 0f &&
            settings.lineHeight == 1.4f &&
            settings.paragraphSpacing == 0.5f

        return EpubPreferences(
            scroll = scroll,
            columnCount = if (scroll) null else ColumnCount.ONE,
            pageMargins = 1.2,
            fontSize = settings.fontScale.toDouble().coerceIn(0.8, 2.0),
            fontFamily = when (settings.fontFamily) {
                ReaderFontFamily.PUBLISHER -> null
                ReaderFontFamily.SANS, ReaderFontFamily.HEITI -> FontFamily("sans-serif")
                ReaderFontFamily.SERIF,
                ReaderFontFamily.SONG,
                ReaderFontFamily.KAITI,
                ReaderFontFamily.FANGSONG -> FontFamily("serif")
                ReaderFontFamily.MONO -> FontFamily("monospace")
                // Readium can't load arbitrary TTF paths; fall back to serif look.
                ReaderFontFamily.CUSTOM -> FontFamily("serif")
            },
            theme = when (settings.themeMode) {
                ReaderThemeMode.LIGHT -> Theme.LIGHT
                ReaderThemeMode.SEPIA -> Theme.SEPIA
                ReaderThemeMode.DARK -> Theme.DARK
            },
            publisherStyles = if (publisherStyles) null else false,
            letterSpacing = settings.letterSpacing.toDouble().takeIf { settings.letterSpacing != 0f },
            lineHeight = settings.lineHeight.toDouble().takeIf { settings.lineHeight != 1.4f },
            paragraphSpacing = settings.paragraphSpacing.toDouble().takeIf { settings.paragraphSpacing != 0.5f }
        )
    }

    fun animatedTurns(settings: ReaderSettings): Boolean {
        return when (settings.pageTurnEffect) {
            PageTurnEffect.SLIDE, PageTurnEffect.FADE -> true
            PageTurnEffect.CURL -> false
        }
    }
}
