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

    fun record(throwable: Throwable, keys: Map<String, String> = emptyMap()) {
        if (BuildConfig.DEBUG) return
        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            diagnosticKeys(keys).forEach { (k, v) -> crashlytics.setCustomKey(k, v) }
            crashlytics.recordException(diagnosticException(throwable))
        }
    }

    /** Debug-only: enable collection then crash so Crashlytics can upload on next launch. */
    fun forceTestCrash() {
        check(BuildConfig.DEBUG) { "Test crash is only available in debug builds" }
        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.isCrashlyticsCollectionEnabled = true
            crashlytics.log("Manual test crash from About screen")
            crashlytics.setCustomKey("test_crash", true)
        }
        throw RuntimeException("Test Crashlytics")
    }
}
