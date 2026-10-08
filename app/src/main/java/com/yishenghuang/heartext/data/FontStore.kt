package com.yishenghuang.heartext.data

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class FontStore(private val context: Context) {
    suspend fun importFromUri(uri: Uri): String? = importStream {
        context.contentResolver.openInputStream(uri)
    }

    internal suspend fun importStream(open: () -> InputStream?): String? = withContext(Dispatchers.IO) {
        var staging: File? = null
        try {
            val directory = File(context.filesDir, "fonts").apply { mkdirs() }
            val temporary = File.createTempFile("font-", ".part", directory)
            staging = temporary
            val coroutine = currentCoroutineContext()
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            val input = open() ?: return@withContext null
            input.use {
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutine.ensureActive()
                        val count = it.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 64L * 1024 * 1024) { "Font exceeds size limit" }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                    }
                    require(total > 0) { "Empty font" }
                    output.fd.sync()
                }
            }
            coroutine.ensureActive()
            requireNotNull(Typeface.Builder(temporary).build()) { "Invalid font" }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val target = File(directory, "font-$hash.ttf")
            coroutine.ensureActive()
            // Immutable content paths also invalidate the reader's Typeface/layout caches.
            if (!target.exists()) Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            target.absolutePath
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            staging?.delete()
        }
    }
}
