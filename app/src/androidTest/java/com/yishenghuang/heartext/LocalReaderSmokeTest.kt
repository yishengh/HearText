package com.yishenghuang.heartext

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yishenghuang.heartext.ui.reader.engine.ReadView
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs only against the isolated APK; never boots a production-configured application. */
@RunWith(AndroidJUnit4::class)
class LocalReaderSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun guestReadsSampleTurnsPageAndRestoresLocationAfterRecreation() {
        val offlineLabel = compose.activity.getString(R.string.auth_continue_offline)
        val libraryLabel = compose.activity.getString(R.string.tab_library)
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText(offlineLabel).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithContentDescription(libraryLabel)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText(offlineLabel).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText(offlineLabel).performClick()
        }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithContentDescription(libraryLabel).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(libraryLabel).performClick()
        compose.onNodeWithContentDescription(libraryLabel).assertIsSelected()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Alice", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText("Alice", substring = true).onFirst().performClick()
        val readLabel = compose.activity.getString(R.string.keep_reading)
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText(readLabel).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(readLabel).performScrollTo().assertIsDisplayed().performClick()
        waitForReader()
        var before: Pair<Int, Int>? = null
        compose.runOnUiThread {
            val view = reader()!!
            before = view.getCurrentLocation()
        }
        // The visible page can finish layout before its adjacent page finishes preloading.
        compose.waitUntil(30_000) {
            var accepted = false
            compose.runOnUiThread { accepted = reader()?.turnToNextPage() == true }
            accepted
        }
        compose.waitUntil(30_000) {
            var changed = false
            compose.runOnUiThread { changed = reader()?.getCurrentLocation()?.let { it != before } == true }
            changed
        }
        var expected: Pair<Int, Int>? = null
        compose.runOnUiThread { expected = reader()!!.getCurrentLocation() }
        val app = compose.activity.application as HearTextApp
        compose.waitUntil(30_000) {
            val book = runBlocking { app.container.bookRepository.getBook("OL138052W") }
            book?.let { it.lastChapterIndex to it.lastOffset } == expected
        }
        compose.activityRule.scenario.recreate()
        waitForReader()
        compose.runOnUiThread { assertEquals(expected, reader()!!.getCurrentLocation()) }
    }

    private fun waitForReader() {
        try {
            compose.waitUntil(30_000) {
                var ready = false
                compose.runOnUiThread { ready = reader()?.getCurrentPageStartCharacterOffset() != null }
                ready
            }
        } catch (failure: Throwable) {
            compose.onRoot(useUnmergedTree = true).printToLog("HearTextSmoke")
            compose.runOnUiThread {
                fun describe(view: View, depth: Int = 0) {
                    android.util.Log.i("HearTextSmoke", "${" ".repeat(depth)}${view.javaClass.name} ${view.width}x${view.height}")
                    if (view is ViewGroup) for (i in 0 until view.childCount) describe(view.getChildAt(i), depth + 1)
                }
                describe(compose.activity.window.decorView)
            }
            throw failure
        }
    }

    private fun reader(): ReadView? = findReader(compose.activity.window.decorView)

    private fun findReader(view: View): ReadView? {
        if (view is ReadView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findReader(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    companion object {
        @JvmStatic @BeforeClass fun requireLocalValidationBuild() {
            check(BuildConfig.APPLICATION_ID.endsWith(".validation")) {
                "Run with -PheartextValidation=true; production-configured device tests are forbidden"
            }
            check(BuildConfig.CLERK_PUBLISHABLE_KEY.isBlank())
            check(BuildConfig.API_BASE_URL == "http://10.0.2.2:18080")
        }
    }
}
