package com.yishenghuang.heartext.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yishenghuang.heartext.HearTextApp
import com.yishenghuang.heartext.R
import com.yishenghuang.heartext.data.ReaderFontFamily
import com.yishenghuang.heartext.ui.reader.ReaderSettingsSheet
import kotlinx.coroutines.launch

@Composable
fun TypographySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as HearTextApp
    val prefs = app.container.readerPreferences
    val settings by prefs.settings.collectAsStateWithLifecycle()
    val fontStore = app.container.fontStore
    val scope = rememberCoroutineScope()

    SettingsSubpageScaffold(title = stringResource(R.string.typography_title), onBack = onBack, scrollable = false) {
        ReaderSettingsSheet(
            settings = settings,
            listeningEnabled = true,
            curlAvailable = true,
            onFontScale = { v -> prefs.update { it.copy(fontScale = v.coerceIn(0.8f, 2f)) } },
            onTheme = { m -> prefs.update { it.copy(themeMode = m) } },
            onBrightness = { v -> prefs.update { it.copy(brightness = v.coerceIn(0.3f, 1f)) } },
            onPageTurn = { e -> prefs.update { it.copy(pageTurnEffect = e) } },
            onFontFamily = { f -> prefs.update { it.copy(fontFamily = f) } },
            onLetterSpacing = { v -> prefs.update { it.copy(letterSpacing = v) } },
            onLineHeight = { v -> prefs.update { it.copy(lineHeight = v) } },
            onParagraphSpacing = { v -> prefs.update { it.copy(paragraphSpacing = v) } },
            onChineseMode = { mode ->
                val resolved = mode.takeIf { it in setOf("original", "simplified", "traditional") }
                    ?: "original"
                prefs.update { it.copy(chineseMode = resolved) }
            },
            showChineseScript = true,
            onVolumeKeyPageTurn = { enabled ->
                prefs.update { it.copy(volumeKeyPageTurn = enabled) }
            },
            onImportFont = { uri ->
                scope.launch {
                    val path = fontStore.importFromUri(uri) ?: return@launch
                    prefs.update {
                        it.copy(
                            customFontPath = path,
                            fontFamily = ReaderFontFamily.CUSTOM
                        )
                    }
                }
            }
        )
    }
}
