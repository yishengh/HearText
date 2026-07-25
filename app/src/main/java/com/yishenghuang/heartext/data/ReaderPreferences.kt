package com.yishenghuang.heartext.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ReaderThemeMode { LIGHT, SEPIA, DARK }

enum class PageTurnEffect {
    SLIDE,
    FADE,
    CURL
}

enum class ReaderFontFamily {
    PUBLISHER,
    SANS,
    SERIF,
    MONO,
    /** Chinese sans (黑体), prefers system CJK. */
    HEITI,
    /** Chinese serif (宋体). */
    SONG,
    /** Chinese Kai style (楷体). */
    KAITI,
    /** Chinese FangSong style (仿宋). */
    FANGSONG,
    CUSTOM
}

/** Which engine the reader uses for Listen. */
enum class TtsVoiceSource {
    SYSTEM,
    OFFLINE
}

data class ReaderSettings(
    val fontScale: Float = 1f,
    val themeMode: ReaderThemeMode = ReaderThemeMode.LIGHT,
    val brightness: Float = 1f,
    val ttsVoiceSource: TtsVoiceSource = TtsVoiceSource.SYSTEM,
    /** Installed offline voice UUID, when [ttsVoiceSource] is OFFLINE. */
    val selectedOfflineVoiceId: String? = null,
    /** TTS playback rate, typically 0.75–2.0. */
    val ttsSpeed: Float = 1f,
    val pageTurnEffect: PageTurnEffect = PageTurnEffect.SLIDE,
    val fontFamily: ReaderFontFamily = ReaderFontFamily.PUBLISHER,
    /** Absolute path to imported TTF/OTF, or null. */
    val customFontPath: String? = null,
    val letterSpacing: Float = 0f,
    val lineHeight: Float = 1.4f,
    val paragraphSpacing: Float = 0.5f,
    /** Reader script: original | simplified | traditional */
    val chineseMode: String = "original",
    /** Use volume up/down to turn pages while reading. */
    val volumeKeyPageTurn: Boolean = true
)

class ReaderPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    private fun read(): ReaderSettings {
        val customPath = prefs.getString("custom_font_path", null)?.takeIf {
            java.io.File(it).exists()
        }
        var family = prefs.getString("font_family", null)?.let {
            runCatching { ReaderFontFamily.valueOf(it) }.getOrDefault(ReaderFontFamily.PUBLISHER)
        } ?: ReaderFontFamily.PUBLISHER
        if (family == ReaderFontFamily.CUSTOM && customPath == null) {
            family = ReaderFontFamily.SANS
        }
        val offlineId = prefs.getString("selected_offline_voice_id", null)?.takeIf { it.isNotBlank() }
        val rawSource = prefs.getString("tts_voice_source", null)
        val source = when (rawSource) {
            TtsVoiceSource.OFFLINE.name -> TtsVoiceSource.OFFLINE
            // Legacy AI preference → system TTS
            "AI", TtsVoiceSource.SYSTEM.name, null -> TtsVoiceSource.SYSTEM
            else -> TtsVoiceSource.SYSTEM
        }.let { resolved ->
            if (resolved == TtsVoiceSource.OFFLINE && offlineId == null) TtsVoiceSource.SYSTEM
            else resolved
        }
        return ReaderSettings(
            fontScale = prefs.getFloat("font_scale", 1f),
            themeMode = prefs.getString("theme_mode", null)?.let {
                runCatching { ReaderThemeMode.valueOf(it) }.getOrDefault(ReaderThemeMode.LIGHT)
            } ?: ReaderThemeMode.LIGHT,
            brightness = prefs.getFloat("brightness", 1f),
            ttsVoiceSource = source,
            selectedOfflineVoiceId = offlineId.takeIf { source == TtsVoiceSource.OFFLINE },
            ttsSpeed = prefs.getFloat("tts_speed", 1f).coerceIn(0.5f, 2.5f),
            pageTurnEffect = prefs.getString("page_turn", null)?.let {
                when (it) {
                    "SCROLL" -> PageTurnEffect.SLIDE
                    else -> runCatching { PageTurnEffect.valueOf(it) }.getOrDefault(PageTurnEffect.SLIDE)
                }
            } ?: PageTurnEffect.SLIDE,
            fontFamily = family,
            customFontPath = customPath,
            letterSpacing = prefs.getFloat("letter_spacing", 0f),
            lineHeight = prefs.getFloat("line_height", 1.4f),
            paragraphSpacing = prefs.getFloat("paragraph_spacing", 0.5f),
            chineseMode = prefs.getString("chinese_mode", "original")
                ?.takeIf { it in setOf("original", "simplified", "traditional") }
                ?: "original",
            volumeKeyPageTurn = prefs.getBoolean("volume_key_page_turn", true)
        )
    }

    fun update(persist: Boolean = true, transform: (ReaderSettings) -> ReaderSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        if (!persist) return
        prefs.edit()
            .putFloat("font_scale", next.fontScale)
            .putString("theme_mode", next.themeMode.name)
            .putFloat("brightness", next.brightness)
            .putBoolean("use_ai_tts", false)
            .putString("tts_voice_source", next.ttsVoiceSource.name)
            .remove("selected_ai_voice_id")
            .putString("selected_offline_voice_id", next.selectedOfflineVoiceId)
            .putFloat("tts_speed", next.ttsSpeed)
            .putString("page_turn", next.pageTurnEffect.name)
            .putString("font_family", next.fontFamily.name)
            .putString("custom_font_path", next.customFontPath)
            .putFloat("letter_spacing", next.letterSpacing)
            .putFloat("line_height", next.lineHeight)
            .putFloat("paragraph_spacing", next.paragraphSpacing)
            .putString("chinese_mode", next.chineseMode)
            .putBoolean("volume_key_page_turn", next.volumeKeyPageTurn)
            .apply()
    }

    fun persistCurrent() {
        update(persist = true) { it }
    }
}
