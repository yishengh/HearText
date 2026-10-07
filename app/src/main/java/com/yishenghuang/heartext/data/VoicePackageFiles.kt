package com.yishenghuang.heartext.data

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

internal object VoicePackageFiles {
    fun safeId(id: String): String {
        require(id.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,127}"))) { "Invalid voice identifier" }
        return id
    }

    fun verifyChecksum(file: File, checksum: String?, checkActive: () -> Unit = {}) {
        if (checksum.isNullOrBlank()) return // Older compatible servers may omit this optional field.
        require(checksum.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid voice checksum" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                checkActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(actual.equals(checksum, ignoreCase = true)) { "Voice checksum mismatch" }
    }

    fun entryFile(directory: File, name: String): File {
        val normalized = name.replace('\\', '/')
        require(normalized.isNotBlank() && !normalized.startsWith('/') && ':' !in normalized &&
            normalized.split('/').none { it == ".." }) { "Unsafe archive path" }
        val target = File(directory, normalized).canonicalFile
        require(target.toPath().startsWith(directory.canonicalFile.toPath()) && target != directory.canonicalFile) {
            "Archive path escapes destination"
        }
        return target
    }

    fun unzip(archive: File, directory: File, checkActive: () -> Unit = {}) {
        var total = 0L
        var entries = 0
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                checkActive()
                val entry = zip.nextEntry ?: break
                require(++entries <= 20_000) { "Too many voice files" }
                val target = entryFile(directory, entry.name)
                if (entry.isDirectory) target.mkdirs() else {
                    target.parentFile?.mkdirs()
                    var fileBytes = 0L
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            checkActive()
                            val count = zip.read(buffer)
                            if (count < 0) break
                            fileBytes += count
                            total += count
                            require(fileBytes <= 512L * 1024 * 1024 && total <= 1024L * 1024 * 1024) { "Voice package is too large" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    @Synchronized fun recover(destination: File) {
        val backup = File(destination.parentFile, ".${destination.name}.backup")
        if (!destination.exists() && backup.isDirectory) check(backup.renameTo(destination)) { "Cannot recover previous voice" }
    }

    /** Called only after validation, without suspension; retain a rollback copy until commit. */
    @Synchronized fun commit(staging: File, destination: File) {
        val parent = requireNotNull(destination.canonicalFile.parentFile)
        require(staging.canonicalFile.toPath().startsWith(parent.toPath()) &&
            !staging.canonicalFile.toPath().startsWith(destination.canonicalFile.toPath()))
        recover(destination)
        val backup = File(destination.parentFile, ".${destination.name}.backup")
        if (backup.exists()) check(backup.deleteRecursively()) { "Cannot clear voice backup" }
        val hadPrevious = destination.exists()
        if (hadPrevious) check(destination.renameTo(backup)) { "Cannot preserve previous voice" }
        if (!staging.renameTo(destination)) {
            if (hadPrevious) check(backup.renameTo(destination)) { "Cannot restore previous voice" }
            error("Cannot install voice")
        }
        backup.deleteRecursively()
    }
}
