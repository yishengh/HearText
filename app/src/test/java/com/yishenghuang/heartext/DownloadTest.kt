package com.yishenghuang.heartext

import com.yishenghuang.heartext.network.atomicDownload
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.CancellationException

class DownloadTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun completeDownloadReplacesExistingFileAndReportsProgress() {
        val destination = folder.newFile("book.epub").apply { writeText("old") }
        var bytes = 0L
        atomicDownload(ByteArrayInputStream("new book".toByteArray()), destination, 8,
            onProgress = { read, total -> bytes = read; assertEquals(8L, total) })
        assertEquals("new book", destination.readText())
        assertEquals(8L, bytes)
        assertEquals(listOf("book.epub"), folder.root.list()!!.toList())
    }

    @Test fun truncatedAndEmptyTransfersPreserveOldFile() {
        val destination = folder.newFile("book.epub").apply { writeText("original") }
        for (bytes in listOf("short", "")) {
            assertThrows(IOException::class.java) {
                atomicDownload(ByteArrayInputStream(bytes.toByteArray()), destination, 20)
            }
            assertEquals("original", destination.readText())
            assertEquals(1, folder.root.list()!!.size)
        }
    }

    @Test fun cancellationBeforeCommitKeepsOriginalAndRemovesStaging() {
        val destination = folder.newFile("book.epub").apply { writeText("original") }
        var cancelled = false
        assertThrows(CancellationException::class.java) {
            atomicDownload(ByteArrayInputStream("replacement".toByteArray()), destination, 11,
                checkActive = { if (cancelled) throw CancellationException() },
                onProgress = { _, _ -> cancelled = true })
        }
        assertEquals("original", destination.readText())
        assertEquals(1, folder.root.list()!!.size)
    }
}
