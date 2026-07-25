package com.yishenghuang.heartext.data

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Typeface
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves reader font families to concrete [Typeface]s.
 * Latin options use bundled fonts so Sans / Serif / Mono stay visually distinct.
 * Chinese options prefer bundled or system CJK fonts (not the same serif fallback).
 */
object ReaderTypefaces {

    private const val ASSET_SANS = "fonts/SourceSans3-Regular.ttf"
    private const val ASSET_SERIF = "fonts/Lora-Regular.ttf"
    private const val ASSET_MONO = "fonts/JetBrainsMono-Regular.ttf"
    private const val ASSET_KAITI = "fonts/LXGWWenKai-Regular.ttf"
    private const val ASSET_FANGSONG = "fonts/ZCOOLXiaoWei-Regular.ttf"

    @Volatile
    private var assets: AssetManager? = null

    private val cache = ConcurrentHashMap<String, Typeface>()

    fun init(context: Context) {
        assets = context.applicationContext.assets
        cache.clear()
    }

    fun typeface(
        family: ReaderFontFamily,
        customFontPath: String? = null
    ): Typeface = when (family) {
        ReaderFontFamily.PUBLISHER -> Typeface.DEFAULT
        ReaderFontFamily.SANS -> assetOr(ASSET_SANS, Typeface.SANS_SERIF)
        ReaderFontFamily.SERIF -> assetOr(ASSET_SERIF, Typeface.SERIF)
        ReaderFontFamily.MONO -> assetOr(ASSET_MONO, Typeface.MONOSPACE)
        ReaderFontFamily.HEITI -> firstAvailable(
            assetPaths = emptyList(),
            files = HEITI_FILES,
            names = HEITI_NAMES,
            fallback = assetOr(ASSET_SANS, Typeface.SANS_SERIF)
        )
        ReaderFontFamily.SONG -> firstAvailable(
            assetPaths = emptyList(),
            files = SONG_FILES,
            names = SONG_NAMES,
            fallback = assetOr(ASSET_SERIF, Typeface.SERIF)
        )
        ReaderFontFamily.KAITI -> firstAvailable(
            assetPaths = listOf(ASSET_KAITI),
            files = KAITI_FILES,
            names = KAITI_NAMES,
            fallback = assetOr(ASSET_SERIF, Typeface.SERIF)
        )
        ReaderFontFamily.FANGSONG -> firstAvailable(
            assetPaths = listOf(ASSET_FANGSONG),
            files = FANGSONG_FILES,
            names = FANGSONG_NAMES,
            fallback = assetOr(ASSET_SERIF, Typeface.SERIF)
        )
        ReaderFontFamily.CUSTOM -> {
            val path = customFontPath
            if (path != null && File(path).exists()) {
                cache.getOrPut("custom:$path") {
                    runCatching { Typeface.createFromFile(path) }.getOrDefault(Typeface.DEFAULT)
                }
            } else {
                Typeface.DEFAULT
            }
        }
    }

    fun fontTypeKey(family: ReaderFontFamily): String = when (family) {
        ReaderFontFamily.PUBLISHER -> "system"
        ReaderFontFamily.SANS -> "sans_serif"
        ReaderFontFamily.SERIF -> "serif"
        ReaderFontFamily.MONO -> "monospace"
        ReaderFontFamily.HEITI -> "heiti"
        ReaderFontFamily.SONG -> "song"
        ReaderFontFamily.KAITI -> "kaiti"
        ReaderFontFamily.FANGSONG -> "fangsong"
        ReaderFontFamily.CUSTOM -> "custom"
    }

    fun fromFontTypeKey(key: String): ReaderFontFamily = when (key) {
        "sans_serif" -> ReaderFontFamily.SANS
        "serif" -> ReaderFontFamily.SERIF
        "monospace" -> ReaderFontFamily.MONO
        "heiti" -> ReaderFontFamily.HEITI
        "song" -> ReaderFontFamily.SONG
        "kaiti" -> ReaderFontFamily.KAITI
        "fangsong" -> ReaderFontFamily.FANGSONG
        "custom" -> ReaderFontFamily.CUSTOM
        else -> ReaderFontFamily.PUBLISHER
    }

    private fun assetOr(assetPath: String, fallback: Typeface): Typeface =
        fromAsset(assetPath) ?: fallback

    private fun fromAsset(assetPath: String): Typeface? {
        val am = assets ?: return null
        cache[assetPath]?.let { return it }
        val loaded = runCatching { Typeface.createFromAsset(am, assetPath) }.getOrNull() ?: return null
        cache[assetPath] = loaded
        return loaded
    }

    private fun firstAvailable(
        assetPaths: List<String>,
        files: List<String>,
        names: List<String>,
        fallback: Typeface
    ): Typeface {
        for (path in assetPaths) {
            fromAsset(path)?.let { return it }
        }
        for (path in files) {
            val file = File(path)
            if (!file.exists()) continue
            val key = "file:$path"
            cache[key]?.let { return it }
            val loaded = runCatching { Typeface.createFromFile(file) }.getOrNull() ?: continue
            cache[key] = loaded
            return loaded
        }
        for (name in names) {
            val key = "name:$name"
            cache[key]?.let { return it }
            val created = Typeface.create(name, Typeface.NORMAL) ?: continue
            if (created === Typeface.DEFAULT) continue
            cache[key] = created
            return created
        }
        return fallback
    }

    private val HEITI_FILES = listOf(
        "/system/fonts/NotoSansCJK-Regular.ttc",
        "/system/fonts/NotoSansSC-Regular.otf",
        "/system/fonts/NotoSansCJKsc-Regular.otf",
        "/system/fonts/DroidSansFallback.ttf",
        "/system/fonts/DroidSansChinese.ttf"
    )

    private val SONG_FILES = listOf(
        "/system/fonts/NotoSerifCJK-Regular.ttc",
        "/system/fonts/NotoSerifSC-Regular.otf",
        "/system/fonts/NotoSerifCJKsc-Regular.otf",
        "/system/fonts/NotoSerifCJK-Regular.otf"
    )

    // Avoid pointing 楷体/仿宋 at the same NotoSerifCJK used by 宋体.
    private val KAITI_FILES = listOf(
        "/system/fonts/STKaiti.ttf",
        "/system/fonts/KaiTi.ttf"
    )

    private val FANGSONG_FILES = listOf(
        "/system/fonts/STFangsong.ttf",
        "/system/fonts/FangSong.ttf"
    )

    private val HEITI_NAMES = listOf(
        "Noto Sans CJK SC",
        "Noto Sans CJK",
        "Source Han Sans CN",
        "sans-serif-medium",
        "sans-serif"
    )

    private val SONG_NAMES = listOf(
        "Noto Serif CJK SC",
        "Noto Serif CJK",
        "Source Han Serif CN",
        "serif"
    )

    private val KAITI_NAMES = listOf("KaiTi", "STKaiti", "cursive")

    private val FANGSONG_NAMES = listOf("FangSong", "STFangsong")
}
