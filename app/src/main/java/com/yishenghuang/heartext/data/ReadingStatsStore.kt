package com.yishenghuang.heartext.data

import com.yishenghuang.heartext.util.localizedString

import android.content.Context
import com.yishenghuang.heartext.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Accumulates on-device reading time while the reader is in the foreground.
 */
class ReadingStatsStore(context: Context) {
    private val prefs = context.getSharedPreferences("reading_stats", Context.MODE_PRIVATE)

    private val _totalReadingMs = MutableStateFlow(prefs.getLong(KEY_TOTAL_MS, 0L))
    val totalReadingMs: StateFlow<Long> = _totalReadingMs.asStateFlow()

    @Volatile
    private var sessionStartedAt: Long = 0L

    fun startSession() {
        if (sessionStartedAt == 0L) {
            sessionStartedAt = System.currentTimeMillis()
        }
    }

    fun endSession() {
        val started = sessionStartedAt
        if (started <= 0L) return
        val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(0L)
        sessionStartedAt = 0L
        if (elapsed < 1_000L) return // ignore tiny blips
        val next = prefs.getLong(KEY_TOTAL_MS, 0L) + elapsed
        prefs.edit().putLong(KEY_TOTAL_MS, next).apply()
        _totalReadingMs.value = next
    }

    companion object {
        private const val KEY_TOTAL_MS = "total_reading_ms"
    }
}

data class LibraryStats(
    val totalBooks: Int = 0,
    val readingBooks: Int = 0,
    val finishedBooks: Int = 0,
    val storeBooks: Int = 0,
    val totalReadingMs: Long = 0L
) {
    val readingHours: Float
        get() = totalReadingMs / 3_600_000f

    fun formatReadingTime(context: Context): String {
        val totalMin = (totalReadingMs / 60_000L).coerceAtLeast(0L)
        val hours = (totalMin / 60L).toInt()
        val minutes = (totalMin % 60L).toInt()
        return when {
            totalMin <= 0L -> context.localizedString(R.string.profile_time_zero_min)
            hours <= 0 -> context.localizedString(R.string.profile_time_mins, minutes)
            else -> context.localizedString(R.string.profile_time_hours_mins, hours, minutes)
        }
    }
}
