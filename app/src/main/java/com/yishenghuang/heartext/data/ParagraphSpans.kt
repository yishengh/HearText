package com.yishenghuang.heartext.data

import android.graphics.Paint
import android.text.style.LineHeightSpan

/** Paragraph spacing spans used by the Lumi-port page layout engine. */
object ParagraphSpans {
    class ParagraphLineHeightSpan(val extraHeightPx: Int) : LineHeightSpan {
        override fun chooseHeight(
            text: CharSequence,
            start: Int,
            end: Int,
            spanstartv: Int,
            lineHeight: Int,
            fm: Paint.FontMetricsInt
        ) {
            val isSpacerLine = start < end && (start until end).all { index ->
                text[index] == '\n' || text[index] == '\r'
            }
            if (!isSpacerLine) return
            fm.ascent = 0
            fm.top = 0
            fm.descent = extraHeightPx
            fm.bottom = extraHeightPx
        }
    }
}
