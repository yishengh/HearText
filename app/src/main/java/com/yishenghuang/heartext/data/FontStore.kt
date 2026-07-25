package com.yishenghuang.heartext.data

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FontStore(private val context: Context) {
    private val fontsDir: File
        get() = File(context.filesDir, "fonts").also { it.mkdirs() }

    fun customFontFile(): File = File(fontsDir, "custom_font.ttf")

    fun hasCustomFont(): Boolean {
        val f = customFontFile()
        return f.exists() && f.length() > 0
    }

    fun customFontPathOrNull(): String? =
        customFontFile().takeIf { it.exists() && it.length() > 0 }?.absolutePath

    fun loadCustomTypeface(): Typeface? {
        val path = customFontPathOrNull() ?: return null
        return runCatching { Typeface.createFromFile(path) }.getOrNull()
    }

    suspend fun importFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = customFontFile()
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            if (target.exists() && target.length() > 0) target.absolutePath else null
        }.getOrNull()
    }
}
