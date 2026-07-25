package com.yishenghuang.heartext.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.abs

enum class CoverSource {
    /** Downloaded from backend / catalog `cover_url`. */
    REMOTE,
    /** Embedded EPUB cover image. */
    EPUB,
    /** Locally generated placeholder art. */
    GENERATED,
    /** User-picked image; never overwrite on sync. */
    USER
}

/**
 * Resolves and stores book cover images under `filesDir/covers/`.
 *
 * Priority: USER → remote URL → EPUB bytes → generated art.
 */
class CoverStore(private val context: Context) {

    private val coversDir: File
        get() = File(context.filesDir, "covers").also { it.mkdirs() }

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun coverFile(bookId: String): File = File(coversDir, "$bookId.jpg")

    fun hasUsableCover(path: String?): Boolean =
        !path.isNullOrBlank() && File(path).exists() && File(path).length() > 0L

    suspend fun downloadRemote(bookId: String, url: String): String? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        runCatching {
            val request = Request.Builder().url(url).get().build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val bytes = response.body?.bytes() ?: return@runCatching null
                if (bytes.isEmpty()) return@runCatching null
                val dest = coverFile(bookId)
                dest.writeBytes(bytes)
                dest.absolutePath
            }
        }.getOrNull()
    }

    suspend fun saveBytes(bookId: String, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        if (bytes.isEmpty()) return@withContext null
        runCatching {
            val dest = coverFile(bookId)
            dest.writeBytes(bytes)
            dest.absolutePath
        }.getOrNull()
    }

    suspend fun saveUserCover(bookId: String, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dest = coverFile(bookId)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            if (!dest.exists() || dest.length() == 0L) return@runCatching null
            dest.absolutePath
        }.getOrNull()
    }

    suspend fun generate(bookId: String, title: String, author: String): String =
        withContext(Dispatchers.IO) {
            val dest = coverFile(bookId)
            val bitmap = renderGeneratedCover(title, author)
            FileOutputStream(dest).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            bitmap.recycle()
            dest.absolutePath
        }

    /**
     * Resolve local cover path + source for a book.
     * Preserves USER covers. Prefers [remoteUrl], then [epubBytes], else generates.
     */
    suspend fun resolve(
        bookId: String,
        title: String,
        author: String,
        remoteUrl: String? = null,
        epubBytes: ByteArray? = null,
        existingPath: String? = null,
        existingSource: CoverSource? = null
    ): Pair<String, CoverSource> {
        if (existingSource == CoverSource.USER && hasUsableCover(existingPath)) {
            return existingPath!! to CoverSource.USER
        }
        if (!remoteUrl.isNullOrBlank()) {
            downloadRemote(bookId, remoteUrl)?.let { return it to CoverSource.REMOTE }
        }
        if (epubBytes != null && epubBytes.isNotEmpty()) {
            saveBytes(bookId, epubBytes)?.let { return it to CoverSource.EPUB }
        }
        if (hasUsableCover(existingPath) && existingSource != null && existingSource != CoverSource.GENERATED) {
            return existingPath!! to existingSource
        }
        if (hasUsableCover(existingPath) && existingSource == CoverSource.GENERATED) {
            return existingPath!! to CoverSource.GENERATED
        }
        val generated = generate(bookId, title, author)
        return generated to CoverSource.GENERATED
    }

    fun delete(bookId: String) {
        runCatching { coverFile(bookId).delete() }
    }

    private fun renderGeneratedCover(title: String, author: String): Bitmap {
        val width = 600
        val height = 900
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val seed = abs((title + author).hashCode())
        val (c1, c2) = palette[seed % palette.size]
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                c1, c2, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Soft panel
        val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x33FFFFFF
        }
        canvas.drawRoundRect(
            RectF(48f, height * 0.28f, width - 48f, height * 0.78f),
            28f, 28f, panel
        )

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 54f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val displayTitle = title.ifBlank { "Untitled" }
        val titleLayout = StaticLayout.Builder
            .obtain(displayTitle, 0, displayTitle.length, titlePaint, width - 120)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(4)
            .setLineSpacing(0f, 1.15f)
            .setIncludePad(false)
            .build()
        canvas.save()
        canvas.translate(60f, height * 0.36f)
        titleLayout.draw(canvas)
        canvas.restore()

        val authorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xE6FFFFFF.toInt()
            textSize = 32f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val displayAuthor = author.ifBlank { "Unknown" }
        val authorLayout = StaticLayout.Builder
            .obtain(displayAuthor, 0, displayAuthor.length, authorPaint, width - 120)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .setIncludePad(false)
            .build()
        canvas.save()
        canvas.translate(60f, height * 0.36f + titleLayout.height + 36f)
        authorLayout.draw(canvas)
        canvas.restore()

        // Brand mark
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCFFFFFF.toInt()
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("HearText", width / 2f, height - 64f, brandPaint)
        return bitmap
    }

    companion object {
        private val palette = listOf(
            0xFF5B4BDB.toInt() to 0xFF2C2158.toInt(),
            0xFF0F766E.toInt() to 0xFF134E4A.toInt(),
            0xFFB45309.toInt() to 0xFF7C2D12.toInt(),
            0xFFBE123C.toInt() to 0xFF4C0519.toInt(),
            0xFF1D4ED8.toInt() to 0xFF1E3A8A.toInt(),
            0xFF6D28D9.toInt() to 0xFF4C1D95.toInt(),
            0xFF0E7490.toInt() to 0xFF164E63.toInt(),
            0xFFA16207.toInt() to 0xFF713F12.toInt()
        )
    }
}
