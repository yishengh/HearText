package com.yishenghuang.heartext.util

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.yishenghuang.heartext.BuildConfig

/**
 * Thin Crashlytics wrapper. Safe when [google-services.json] is missing
 * (Firebase not initialized) — all calls no-op via [runCatching].
 */
object CrashReporting {
    fun init() {
        runCatching {
            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
        }
    }

    fun log(message: String) {
        runCatching { FirebaseCrashlytics.getInstance().log(message) }
    }

    fun setKey(key: String, value: String) {
        runCatching { FirebaseCrashlytics.getInstance().setCustomKey(key, value) }
    }

    fun record(throwable: Throwable, keys: Map<String, String> = emptyMap()) {
        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            keys.forEach { (k, v) -> crashlytics.setCustomKey(k, v) }
            crashlytics.recordException(throwable)
        }
    }

    /** Debug-only: enable collection then crash so Crashlytics can upload on next launch. */
    fun forceTestCrash() {
        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.isCrashlyticsCollectionEnabled = true
            crashlytics.log("Manual test crash from About screen")
            crashlytics.setCustomKey("test_crash", true)
        }
        throw RuntimeException("Test Crashlytics")
    }
}
