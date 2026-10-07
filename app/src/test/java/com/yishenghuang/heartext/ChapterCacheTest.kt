package com.yishenghuang.heartext

import com.yishenghuang.heartext.data.ChapterCache
import com.yishenghuang.heartext.data.EpubChapter
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ChapterCacheTest {
    @Test fun changedFileDoesNotReuseOldChapters() {
        val cache = ChapterCache()
        val chapters = mutableListOf(EpubChapter("A", "a", "Original"))
        cache.put("book", 10, 20, chapters)
        chapters.clear()
        assertEquals("Original", cache.get("book", 10, 20)?.single()?.plainText)
        assertNull(cache.get("book", 11, 20))
        assertNull(cache.get("book", 10, 21))
        assertNull(cache.get("other", 10, 20))
    }

    @Test fun concurrentReadingAndPlaybackNeverReceiveAnotherBooksText() {
        val cache = ChapterCache()
        val workers = Executors.newFixedThreadPool(4)
        try {
            val tasks = (1..4).map { book -> Callable {
                val path = "book-$book"
                val chapters = listOf(EpubChapter(path, path, path))
                repeat(50_000) {
                    cache.put(path, 1, 1, chapters)
                    cache.get(path, 1, 1)?.let { assertEquals(path, it.single().plainText) }
                }
            } }
            workers.invokeAll(tasks).forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            workers.shutdownNow()
        }
    }
}
