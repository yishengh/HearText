package com.yishenghuang.heartext.ui.reader

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator

/** Subscribe to existing and future hosts; fragment creation has no time limit. Main thread only. */
internal fun FragmentManager.observeReadiumLocators(
    tag: String,
    scope: CoroutineScope,
    onLocator: (Locator) -> Unit
): AutoCloseable {
    var observed: Fragment? = null
    var collector: Job? = null
    fun attach(fragment: Fragment) {
        if (fragment.tag != tag || fragment !is ReadiumHostFragment || observed === fragment) return
        collector?.cancel()
        observed = fragment
        collector = scope.launch { fragment.locatorUpdates.collect { onLocator(it) } }
    }
    val callbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentCreated(fm: FragmentManager, f: Fragment, state: Bundle?) = attach(f)
        override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
            if (observed === f) {
                collector?.cancel()
                collector = null
                observed = null
            }
        }
    }
    registerFragmentLifecycleCallbacks(callbacks, false)
    findFragmentByTag(tag)?.let(::attach)
    return AutoCloseable {
        unregisterFragmentLifecycleCallbacks(callbacks)
        collector?.cancel()
    }
}
