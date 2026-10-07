package com.yishenghuang.heartext.network

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Stage in the destination directory so a failed/cancelled transfer preserves the previous file. */
internal fun atomicDownload(
    input: InputStream,
    destination: File,
    contentLength: Long,
    checkActive: () -> Unit = {},
    onProgress: ((Long, Long) -> Unit)? = null
): File {
    val parent = destination.absoluteFile.parentFile ?: throw IOException("Missing download directory")
    if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Unable to create download directory")
    val staging = File.createTempFile("download-", ".part", parent)
    try {
        staging.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                checkActive()
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                total += count
                onProgress?.invoke(total, contentLength)
            }
            if (total == 0L || (contentLength >= 0L && total != contentLength)) {
                throw IOException("Incomplete download")
            }
            output.fd.sync()
        }
        checkActive()
        Files.move(staging.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING)
        return destination
    } finally {
        staging.delete()
    }
}
