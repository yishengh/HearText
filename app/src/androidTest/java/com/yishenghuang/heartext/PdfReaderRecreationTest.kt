package com.yishenghuang.heartext

import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yishenghuang.heartext.ui.reader.ReadiumHostFragment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class PdfReaderRecreationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun pdfReaderReattachesRenderedThirdPageAfterActivityRecreation() {
        check(BuildConfig.APPLICATION_ID.endsWith(".validation"))
        val app = compose.activity.application as HearTextApp
        val file = File(app.cacheDir, "pdf-ui-${UUID.randomUUID()}.pdf")
        val document = PdfDocument()
        try {
            repeat(3) { index ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(300, 400, index + 1).create())
                page.canvas.drawText("Local page ${index + 1}", 20f, 40f, android.graphics.Paint())
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally { document.close() }
        val book = runBlocking { app.container.bookRepository.importFromUri(Uri.fromFile(file)) }
        try {
            val offline = compose.activity.getString(R.string.auth_continue_offline)
            val library = compose.activity.getString(R.string.tab_library)
            compose.waitUntil(30000) {
                compose.onAllNodesWithText(offline).fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithContentDescription(library).fetchSemanticsNodes().isNotEmpty()
            }
            if (compose.onAllNodesWithText(offline).fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText(offline).performClick()
            compose.waitUntil(30000) { compose.onAllNodesWithContentDescription(library).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription(library).performClick()
            compose.waitUntil(30000) { compose.onAllNodesWithTag("library-book-${book.id}").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("library-book-${book.id}").performClick()
            val read = compose.activity.getString(R.string.keep_reading)
            compose.waitUntil(30000) { compose.onAllNodesWithText(read).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(read).performScrollTo().performClick()
            awaitPage(0)
            compose.waitUntil(15000) {
                var accepted = false
                compose.runOnUiThread {
                    val host = compose.activity.supportFragmentManager.findFragmentByTag("readium_${book.id}") as? ReadiumHostFragment
                    val current = host?.locatorUpdates?.replayCache?.lastOrNull()
                    if (current != null) accepted = host.go(current.copy(locations = current.locations.copy(
                        fragments = listOf("page=3"), position = 3, progression = 2.0 / 3, totalProgression = 2.0 / 3)))
                }
                accepted
            }
            awaitPage(2)
            compose.waitUntil(10000) {
                val json = runBlocking { app.container.bookRepository.getBook(book.id) }?.locatorJson
                json != null && org.json.JSONObject(json).getJSONObject("locations").optInt("position") == 3
            }
            compose.activityRule.scenario.recreate()
            awaitPage(2)
            compose.runOnUiThread {
                val host = compose.activity.supportFragmentManager.findFragmentByTag("readium_${book.id}") as ReadiumHostFragment
                assertTrue(host.requireView().isAttachedToWindow)
            }
        } finally {
            runBlocking { app.container.bookRepository.deleteBook(book.id) }
            file.delete()
        }
    }

    private fun awaitPage(expected: Int) {
        compose.waitUntil(20000) {
            var page: Int? = null
            compose.runOnUiThread { page = visiblePage(compose.activity.window.decorView) }
            page == expected
        }
    }

    private fun visiblePage(view: View): Int? {
        if (view.javaClass.name == "com.github.barteksc.pdfviewer.PDFView") {
            val count = view.javaClass.getMethod("getPageCount").invoke(view) as Int
            return if (count > 0) view.javaClass.getMethod("getCurrentPage").invoke(view) as Int else null
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) visiblePage(view.getChildAt(index))?.let { return it }
        return null
    }
}
