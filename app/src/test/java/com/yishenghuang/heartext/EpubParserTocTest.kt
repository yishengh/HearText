package com.yishenghuang.heartext

import com.yishenghuang.heartext.data.EpubParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EpubParserTocTest {
    @Test
    fun emmaTocUsesChapterLabelsNotGutenbergTitle() {
        val epub = File("src/test/resources/emma.epub")
        assertTrue("emma.epub missing — download pg158", epub.exists())
        val book = EpubParser.parse(epub)
        val titles = book.chapters.map { it.title }
        assertTrue(titles.isNotEmpty())
        assertFalse(
            "still using HTML <title> dump: ${titles.take(5)}",
            titles.any { it.contains("Project Gutenberg", ignoreCase = true) }
        )
        assertTrue(
            "expected CHAPTER labels, got: ${titles.take(12)}",
            titles.any { it.contains("CHAPTER", ignoreCase = true) }
        )
        // Soft-wrap reflow: a chapter body should not be mostly one-word lines
        val sample = book.chapters.first { it.title.contains("CHAPTER", ignoreCase = true) }.plainText
        val shortLines = sample.lines().count { it.isNotBlank() && it.length < 12 }
        assertTrue("text still fragmented: shortLines=$shortLines", shortLines < 40)
    }
}
