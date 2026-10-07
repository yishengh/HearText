package com.yishenghuang.heartext.data

/** Publishes the file identity and its chapters together to reader and playback workers. */
internal class ChapterCache {
    private data class Entry(
        val path: String,
        val length: Long,
        val modified: Long,
        val chapters: List<EpubChapter>
    )

    @Volatile private var entry: Entry? = null

    fun get(path: String, length: Long, modified: Long): List<EpubChapter>? {
        val snapshot = entry ?: return null
        return snapshot.chapters.takeIf {
            snapshot.path == path && snapshot.length == length && snapshot.modified == modified
        }
    }

    fun put(path: String, length: Long, modified: Long, value: List<EpubChapter>) {
        entry = Entry(path, length, modified, value.toList())
    }
}
