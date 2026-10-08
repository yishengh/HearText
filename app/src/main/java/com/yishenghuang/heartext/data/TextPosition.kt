package com.yishenghuang.heartext.data

import org.json.JSONObject

/** Separate namespace from Readium locators; legacy page offsets remain unchanged. */
internal data class TextPosition(val chapter: Int, val character: Int) {
    fun encode(): String = JSONObject().put("heartextTextPositionVersion", 1)
        .put("chapter", chapter).put("character", character).toString()

    companion object {
        fun decode(json: String?): TextPosition? = try {
            val value = JSONObject(json ?: "")
            if (value.optInt("heartextTextPositionVersion") != 1) null
            else {
                val chapter = value.getInt("chapter")
                val character = value.getInt("character")
                if (chapter < 0 || character < 0) null else TextPosition(chapter, character)
            }
        } catch (_: Exception) { null }
    }
}
