package com.yishenghuang.heartext.util

import android.text.SpannableStringBuilder
import android.text.Spanned
import com.github.houbb.opencc4j.util.ZhConverterUtil

/**
 * Converts between Simplified and Traditional Chinese for reader display.
 * Modes: `original` | `simplified` | `traditional`
 */
object ChineseConverter {
    fun convert(text: String, mode: String): String {
        if (text.isEmpty() || mode == "original") return text
        return when (mode) {
            "simplified" -> ZhConverterUtil.toSimple(text)
            "traditional" -> ZhConverterUtil.toTraditional(text)
            else -> text
        }
    }

    fun convertPreservingSpans(source: CharSequence, mode: String): CharSequence {
        if (mode == "original" || source.isEmpty()) return source
        val converted = convert(source.toString(), mode)
        if (source !is Spanned) return converted
        if (converted.length == source.length) {
            val out = SpannableStringBuilder(converted)
            source.getSpans(0, source.length, Any::class.java).forEach { span ->
                val start = source.getSpanStart(span)
                val end = source.getSpanEnd(span)
                val flags = source.getSpanFlags(span)
                if (start in 0..converted.length && end in start..converted.length) {
                    out.setSpan(span, start, end, flags)
                }
            }
            return out
        }
        // Length changed — keep text conversion; drop fragile absolute spans.
        return converted
    }
}
