package com.yishenghuang.heartext

import com.yishenghuang.heartext.network.*
import org.junit.Assert.*
import org.junit.Test

class RetryAfterGateTest {
    @Test fun secondsDatesInvalidAndOverflowAreHandled() {
        assertEquals(120000L, parseRetryAfter("120", 0))
        assertEquals(1000L, parseRetryAfter("Thu, 1 Jan 1970 00:00:01 GMT", 0))
        assertEquals(0L, parseRetryAfter("Thu, 1 Jan 1970 00:00:01 GMT", 2000))
        assertNull(parseRetryAfter("-1", 0))
        assertNull(parseRetryAfter("private server error", 0))
        assertTrue(parseRetryAfter("999999999999999999999999999", 0)!! > 0)
    }

    @Test fun cooldownUsesMonotonicTimeAndShorterResponsesCannotReleaseIt() {
        var elapsed = 100L
        var wall = 0L
        val gate = RetryAfterGate({ elapsed }, { wall })
        gate.record(429, "10")
        elapsed += 1000
        wall = Long.MAX_VALUE
        gate.record(503, "1")
        val failure = runCatching { gate.check() }.exceptionOrNull() as ApiHttpException
        assertEquals(429, failure.code)
        assertEquals(9000L, failure.retryAfterMillis)
        elapsed += 9000
        gate.check()
        assertNull(gate.record(503, null))
        gate.check()
        assertEquals(1000L, gate.record(429, "invalid"))
    }
}
