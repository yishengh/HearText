package com.yishenghuang.heartext.network

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Shared by one API endpoint. Cooldown rejects new work without queuing writes for later. */
internal class RetryAfterGate(
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val wallMillis: () -> Long = System::currentTimeMillis
) {
    private var deadline = 0L
    private var status = 429

    @Synchronized fun check() {
        val remaining = deadline - monotonicMillis()
        if (remaining > 0) throw ApiHttpException(status, "", remaining)
    }

    @Synchronized fun record(code: Int, header: String?): Long? {
        if (code != 429 && code != 503) return null
        val delay = parseRetryAfter(header, wallMillis()) ?: if (code == 429) 1000L else return null
        val now = monotonicMillis()
        if (delay > 0 && now + delay > deadline) {
            deadline = now + delay
            status = code
        }
        return delay
    }
}

internal fun parseRetryAfter(value: String?, nowMillis: Long): Long? {
    val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    // Keep arithmetic safe even for an overflowing server-provided integer.
    val maximum = Long.MAX_VALUE / 4
    if (text.all { it in '0'..'9' }) {
        val seconds = text.toLongOrNull() ?: return maximum
        return if (seconds > maximum / 1000) maximum else seconds * 1000
    }
    return try {
        val time = ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        (time - nowMillis).coerceIn(0, maximum)
    } catch (_: Exception) { null }
}
