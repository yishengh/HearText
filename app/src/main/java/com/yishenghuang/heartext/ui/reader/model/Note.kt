package com.yishenghuang.heartext.ui.reader.model

/** Minimal note model for ReadView highlight stubs (notes UI not ported). */
data class Note(
    val id: Long = 0,
    val bookId: String = "",
    val chapterIndex: Int = 0,
    val startPosition: Int = 0,
    val endPosition: Int = 0,
    val selectedText: String = "",
    val note: String = "",
    val color: String = "#40FFEB3B",
    val createdAt: Long = 0L
)
