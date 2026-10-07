package com.yishenghuang.heartext.data

import java.io.File

object TextBookLoader {
    fun load(file: File): String {
        val bytes = file.readBytes()
        val charset = when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        val text = charset.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        require('\u0000' !in text) { "Invalid text document" }
        return text
    }

    fun titleFromFile(file: File): String = file.nameWithoutExtension
        .replace('_', ' ')
        .replace('-', ' ')

    /**
     * Line-scan chapter split — avoids Regex.findAll over multi-MB strings (can freeze open).
     */
    fun splitChapters(bookTitle: String, text: String): List<EpubChapter> {
        val header = Regex(
            """^第[0-9０-９一二三四五六七八九十百千零〇两兩]+[章节回部卷集].*|^Chapter\s+\d+.*""",
            RegexOption.IGNORE_CASE
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
            // Keep existing chapter indices stable while retaining the previously dropped preface.
            val start = if (idx == 0) 0 else starts[idx]
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
