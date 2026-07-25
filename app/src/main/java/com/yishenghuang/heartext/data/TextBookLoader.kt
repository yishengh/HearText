package com.yishenghuang.heartext.data

import java.io.File

object TextBookLoader {
    fun load(file: File): String = file.readText(Charsets.UTF_8)

    fun titleFromFile(file: File): String = file.nameWithoutExtension
        .replace('_', ' ')
        .replace('-', ' ')

    /**
     * Line-scan chapter split — avoids Regex.findAll over multi-MB strings (can freeze open).
     */
    fun splitChapters(bookTitle: String, text: String): List<EpubChapter> {
        val header = Regex(
            """^第[0-9０-９一二三四五六七八九十百千零〇两兩]+[章节回部卷集].*|^Chapter\s+\d+.*"""
        )
        val starts = ArrayList<Int>(64)
        val titles = ArrayList<String>(64)
        var i = 0
        val n = text.length
        while (i < n) {
            val lineEnd = text.indexOf('\n', i).let { if (it < 0) n else it }
            val line = text.substring(i, lineEnd).trim()
            if (line.isNotEmpty() && header.matches(line)) {
                starts += i
                titles += line.take(40)
            }
            i = lineEnd + 1
        }
        if (starts.size < 2) {
            return listOf(EpubChapter(title = bookTitle, href = "txt", plainText = text))
        }
        val chapters = ArrayList<EpubChapter>(starts.size)
        for (idx in starts.indices) {
            val start = starts[idx]
            val end = starts.getOrNull(idx + 1) ?: n
            val body = text.substring(start, end).trim()
            if (body.isBlank()) continue
            chapters += EpubChapter(
                title = titles[idx].ifBlank { "Chapter ${idx + 1}" },
                href = "txt#$idx",
                plainText = body
            )
        }
        return chapters.ifEmpty {
            listOf(EpubChapter(title = bookTitle, href = "txt", plainText = text))
        }
    }
}
