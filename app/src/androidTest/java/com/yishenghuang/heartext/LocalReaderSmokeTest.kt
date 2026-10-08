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
        compose.onNodeWithContentDescription(libraryLabel).assertIsSelected()
        compose.onNodeWithContentDescription(libraryLabel).performClick()
        compose.onNodeWithContentDescription(libraryLabel).assertIsSelected()
        try {
            compose.waitUntil(30_000) {
                compose.onAllNodesWithTag("library-book-OL138052W").fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: Throwable) {
            val app = compose.activity.application as HearTextApp
            val exists = runBlocking { app.container.bookRepository.getBook("OL138052W") } != null
            val seeded = app.getSharedPreferences("library_state", 0).getBoolean("sample_seeded", false)
            throw AssertionError("Sample visible timeout: databaseEntry=$exists, seeded=$seeded", failure)
        }
        compose.onNodeWithTag("library-book-OL138052W").assertIsDisplayed().performClick()
        val readLabel = compose.activity.getString(R.string.keep_reading)
        try {
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText(readLabel).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: Throwable) {
            compose.onRoot(useUnmergedTree = true).printToLog("HearTextSmoke")
            throw failure
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
        var character = 0
        compose.runOnUiThread { character = reader()!!.getCurrentPageStartCharacterOffset()!! }
        compose.waitUntil(30_000) {
            val saved = runBlocking { app.container.bookRepository.getBook("OL138052W") }
            com.yishenghuang.heartext.data.TextPosition.decode(saved?.locatorJson)?.character == character
        }
        val bookmark = runBlocking {
            app.container.annotationRepository.addBookmark("OL138052W", null, expected!!.first,
                expected!!.second, characterOffset = character)
        }
        val previous = app.container.readerPreferences.settings.value
        try {
            compose.runOnUiThread {
                app.container.readerPreferences.update { it.copy(fontScale = 1.8f) }
            }
            compose.activityRule.scenario.recreate()
            waitForReader()
            compose.runOnUiThread {
                val range = reader()!!.getCurrentPageCharacterRange()!!
                assertTrue("Saved character must remain visible after changed typography", character in range)
            }
            compose.runOnUiThread { reader()!!.jumpToChapter(expected!!.first + 1, 0) }
            compose.waitUntil(30_000) {
                var ready = false
                compose.runOnUiThread {
                    ready = reader()?.getCurrentLocation()?.first == expected!!.first + 1 &&
                        reader()?.getCurrentPageCharacterRange() != null
                }
                ready
            }
            compose.onRoot().performTouchInput { click(center) }
            val bookmarksLabel = compose.activity.getString(R.string.reader_bookmarks)
            compose.waitUntil(5000) { compose.onAllNodesWithText(bookmarksLabel).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(bookmarksLabel).performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.reader_saved_text_position)).performClick()
            compose.waitUntil(30_000) {
                var restored = false
                compose.runOnUiThread {
                    restored = reader()?.getCurrentLocation()?.first == expected!!.first &&
                        reader()?.getCurrentPageCharacterRange()?.contains(character) == true
                }
                restored
            }
        } finally {
            runBlocking { app.container.annotationRepository.delete(bookmark) }
            compose.runOnUiThread { app.container.readerPreferences.update { previous } }
        }

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
