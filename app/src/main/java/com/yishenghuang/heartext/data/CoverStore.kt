package com.yishenghuang.heartext.data

import android.content.Context
import android.graphics.BitmapFactory
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.yishenghuang.heartext.network.consumeCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

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

    fun coverFile(bookId: String): File {
        val name = MessageDigest.getInstance("SHA-256").digest(bookId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(coversDir, "$name.jpg")
    }

    fun hasUsableCover(path: String?): Boolean =
        !path.isNullOrBlank() && File(path).exists() && File(path).length() > 0L

    private suspend fun safelySave(action: suspend () -> String?): String? = try {
        action()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) { null }

    private fun writeImage(bookId: String, input: InputStream, length: Long = -1, checkActive: () -> Unit): String {
        require(length <= 16L * 1024 * 1024)
        val dest = coverFile(bookId)
        val staging = File.createTempFile("cover-", ".part", coversDir)
        try {
            var total = 0L
            staging.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    checkActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= 16L * 1024 * 1024)
                    output.write(buffer, 0, count)
                }
                require(total > 0 && (length < 0 || length == total))
                output.fd.sync()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(staging.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0)
            require(bounds.outWidth <= 16384 && bounds.outHeight <= 16384)
            require(bounds.outWidth.toLong() * bounds.outHeight <= 40_000_000)
            val options = BitmapFactory.Options().apply { inSampleSize = 1 }
            while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 1024) {
                options.inSampleSize *= 2
            }
            requireNotNull(BitmapFactory.decodeFile(staging.path, options)).recycle()
            checkActive()
            Files.move(staging.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
            return dest.absolutePath
        } finally { staging.delete() }
    }

    suspend fun downloadRemote(bookId: String, url: String): String? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        val coroutine = currentCoroutineContext()
        safelySave {
            val request = Request.Builder().url(url).get().build()
            http.newCall(request).consumeCancellable { response ->
                if (!response.isSuccessful) null else {
                    response.body.byteStream().use { input ->
                        writeImage(bookId, input, response.body.contentLength()) { coroutine.ensureActive() }
                    }
                }
            }
        }
    }

    suspend fun saveBytes(bookId: String, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        val coroutine = currentCoroutineContext()
        safelySave { bytes.inputStream().use { writeImage(bookId, it, bytes.size.toLong()) { coroutine.ensureActive() } } }
    }

    suspend fun saveUserCover(bookId: String, uri: Uri): String? = withContext(Dispatchers.IO) {
        val coroutine = currentCoroutineContext()
        safelySave {
            context.contentResolver.openInputStream(uri)?.use { input ->
                writeImage(bookId, input) { coroutine.ensureActive() }
            }
        }
    }

    suspend fun generate(bookId: String, title: String, author: String): String = withContext(Dispatchers.IO) {
        val bitmap = renderGeneratedCover(title, author)
        val bytes = java.io.ByteArrayOutputStream()
        try { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bytes)) }
        finally { bitmap.recycle() }
        val coroutine = currentCoroutineContext()
        bytes.toByteArray().inputStream().use { writeImage(bookId, it) { coroutine.ensureActive() } }
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
        val seed = (title + author).hashCode() and Int.MAX_VALUE
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
