package com.yishenghuang.heartext

import com.yishenghuang.heartext.util.diagnosticException
import com.yishenghuang.heartext.util.diagnosticKeys
import org.junit.Assert.*
import org.junit.Test

class SafeDiagnosticTest {
    @Test fun reportsRetainCallSitesWithoutMessagesCausesOrArbitraryMetadata() {
        val secret = "Private spoken paragraph /storage/private-book.txt"
        val original = IllegalStateException(secret, IllegalArgumentException(secret)).apply {
            addSuppressed(RuntimeException(secret))
        }
        val report = diagnosticException(original)
        assertFalse(report.stackTraceToString().contains(secret))
        assertNull(report.cause)
        assertTrue(report.suppressed.isEmpty())
        assertArrayEquals(original.stackTrace, report.stackTrace)
        assertTrue(report.message!!.contains("IllegalStateException"))
        assertEquals(mapOf("tts_engine" to "offline", "tts_fallback" to "system"),
            diagnosticKeys(mapOf("tts_engine" to "offline", "tts_fallback" to "system",
                "tts_voice_id" to secret, "book" to secret)))
        assertEquals(mapOf("tts_fallback" to "none"),
            diagnosticKeys(mapOf("tts_engine" to secret, "tts_fallback" to secret)))
    }
}
