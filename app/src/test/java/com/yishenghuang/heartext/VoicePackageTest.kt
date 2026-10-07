package com.yishenghuang.heartext

import com.yishenghuang.heartext.data.OnnxMetadata
import com.yishenghuang.heartext.data.VoicePackageFiles
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class VoicePackageTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun checksumRejectsChangedBytesAndInvalidIdentifiers() {
        val file = temp.newFile().apply { writeText("voice data") }
        val checksum = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        VoicePackageFiles.verifyChecksum(file, checksum)
        file.appendText("changed")
        assertThrows(IllegalArgumentException::class.java) { VoicePackageFiles.verifyChecksum(file, checksum) }
        for (id in listOf("../voice", "/voice", "C:voice", "_shared", "a/b", ".hidden")) {
            assertThrows(IllegalArgumentException::class.java) { VoicePackageFiles.safeId(id) }
        }
    }

    @Test fun archivesCannotEscapeStagingDirectory() {
        val archive = temp.newFile()
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../outside")); zip.write(byteArrayOf(1)); zip.closeEntry()
        }
        val stage = temp.newFolder()
        assertThrows(IllegalArgumentException::class.java) { VoicePackageFiles.unzip(archive, stage) }
        assertFalse(File(temp.root, "outside").exists())
    }

    @Test fun failedCommitRestoresPreviousAndSuccessReplacesIt() {
        val old = File(temp.root, "voice").apply { mkdirs(); resolve("old").writeText("keep") }
        assertThrows(IllegalStateException::class.java) { VoicePackageFiles.commit(File(temp.root, "missing"), old) }
        assertEquals("keep", old.resolve("old").readText())
        val stage = File(temp.root, "stage").apply { mkdirs(); resolve("new").writeText("ready") }
        VoicePackageFiles.commit(stage, old)
        assertEquals("ready", old.resolve("new").readText())
        assertFalse(old.resolve("old").exists())
        assertFalse(File(temp.root, ".voice.backup").exists())
    }

    @Test fun interruptedReplacementCanRecoverOnNextLaunch() {
        val backup = File(temp.root, ".voice.backup").apply { mkdirs(); resolve("old").writeText("keep") }
        val destination = File(temp.root, "voice")
        VoicePackageFiles.recover(destination)
        assertEquals("keep", destination.resolve("old").readText())
        assertFalse(backup.exists())
    }

    @Test fun onnxMetadataRequiresStructureAndAllMandatoryValues() {
        val file = temp.newFile()
        file.writeText("sample_rate".repeat(100))
        assertFalse(OnnxMetadata.supportsVits(file))
        val values = mapOf("sample_rate" to "22050", "n_speakers" to "1", "language" to "en-us", "comment" to "piper")
        file.writeBytes(model(values))
        assertTrue(OnnxMetadata.supportsVits(file))
        file.writeBytes(model(values - "n_speakers"))
        assertFalse(OnnxMetadata.supportsVits(file))
        file.writeBytes(model(values + ("sample_rate" to "not a number")))
        assertFalse(OnnxMetadata.supportsVits(file))
        file.writeBytes(model(values).dropLast(1).toByteArray())
        assertFalse(OnnxMetadata.supportsVits(file))
    }

    private fun model(values: Map<String, String>): ByteArray {
        fun field(tag: Int, bytes: ByteArray): ByteArray {
            val output = ByteArrayOutputStream()
            output.write(tag)
            var size = bytes.size
            while (size >= 128) { output.write((size and 127) or 128); size = size ushr 7 }
            output.write(size); output.write(bytes)
            return output.toByteArray()
        }
        return byteArrayOf(8, 8, 58, 1, 0, 66, 2, 16, 13) + values.entries.fold(byteArrayOf()) { bytes, (key, value) ->
            bytes + field(114, field(10, key.toByteArray()) + field(18, value.toByteArray()))
        }
    }
}
