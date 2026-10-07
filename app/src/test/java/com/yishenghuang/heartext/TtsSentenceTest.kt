package com.yishenghuang.heartext

import com.yishenghuang.heartext.tts.TtsController
import org.junit.Assert.*
import org.junit.Test

class TtsSentenceTest {
    @Test fun longUnpunctuatedTextIsNeverTruncated() {
        val text = "听".repeat(521)
        val chunks = TtsController.splitSentencesWithRanges(text)
        assertEquals(text, chunks.joinToString("") { it.text })
        assertTrue(chunks.all { it.text.length <= 120 })
        chunks.forEach { assertEquals(it.text, text.substring(it.start, it.end)) }
    }

    @Test fun chunkBoundariesPreserveSurrogatePairsAndOffsets() {
        val text = "a".repeat(119) + "😀" + "b".repeat(240)
        val chunks = TtsController.splitSentencesWithRanges(text)
        assertEquals(text, chunks.joinToString("") { it.text })
        assertTrue(chunks.none { it.text.last().isHighSurrogate() || it.text.first().isLowSurrogate() })
        for (offset in text.indices) {
            val sentence = chunks[TtsController.sentenceIndexForOffset(text, offset)]
            assertTrue(offset in sentence.start until sentence.end)
        }
    }

    @Test fun naturalBoundariesKeepSourceRanges() {
        val text = "  Hello world!\n第二句。  Fin?"
        val chunks = TtsController.splitSentencesWithRanges(text)
        assertEquals(listOf("Hello world!", "第二句。", "Fin?"), chunks.map { it.text })
        chunks.forEach { assertEquals(it.text, text.substring(it.start, it.end)) }
        assertTrue(TtsController.splitSentencesWithRanges(" \n ").isEmpty())
    }
}
