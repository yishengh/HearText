package com.yishenghuang.heartext

import com.yishenghuang.heartext.data.EpubParser
import com.yishenghuang.heartext.data.TextBookLoader
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocumentParsingTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun textKeepsPrefaceAndRecognizesCaseInsensitiveHeadings() {
        val chapters = TextBookLoader.splitChapters("Book", "Preface\n\nCHAPTER 1\nOne\nchapter 2\nTwo")
        assertEquals(2, chapters.size)
        assertTrue(chapters.first().plainText.startsWith("Preface"))
        assertTrue(chapters[0].plainText.endsWith("One"))
        assertTrue(chapters[1].plainText.endsWith("Two"))
    }

    @Test fun textDecodesBomsAndRejectsBinary() {
        for (charset in listOf(Charsets.UTF_8, Charsets.UTF_16LE, Charsets.UTF_16BE)) {
            val file = temp.newFile()
            file.writeBytes("\uFEFF正文 😀".toByteArray(charset))
            assertEquals("正文 😀", TextBookLoader.load(file))
        }
        val binary = temp.newFile().apply { writeBytes(byteArrayOf(0, 1, 2)) }
        assertThrows(IllegalArgumentException::class.java) { TextBookLoader.load(binary) }
    }

    @Test fun epubKeepsShortChapterAndHandlesSingleQuotesAndUnicodeEntities() {
        val file = epub("<html><head><title>Hidden metadata</title></head><body><p>Hi &#128512; &#x1F642;</p></body></html>")
        val book = EpubParser.parse(file)
        assertEquals(1, book.chapters.size)
        assertEquals("Hi 😀 🙂", book.chapters.single().plainText)
    }

    @Test fun emptyEpubIsAnErrorInsteadOfFakeBookContent() {
        val file = epub("<html><head><title>Only metadata</title></head><body></body></html>")
        assertThrows(IllegalArgumentException::class.java) { EpubParser.parse(file) }
    }

    @Test fun oversizedCompressedChapterIsRejectedBeforeAllocation() {
        val file = epub("x".repeat(8 * 1024 * 1024 + 1))
        assertThrows(IllegalArgumentException::class.java) { EpubParser.parse(file) }
    }

    private fun epub(body: String) = temp.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, text) in mapOf(
                "META-INF/container.xml" to "<container><rootfile full-path = 'book.opf'/></container>",
                "book.opf" to "<package><metadata><dc:title>Book</dc:title></metadata><manifest><item href='one.xhtml' id='one'/></manifest><spine><itemref idref='one'/></spine></package>",
                "one.xhtml" to body
            )) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
    }
}
