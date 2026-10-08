package com.yishenghuang.heartext

import android.graphics.pdf.PdfDocument
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import com.yishenghuang.heartext.readium.PdfLocatorCodec
import androidx.fragment.app.FragmentContainerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.yishenghuang.heartext.ui.reader.ReadiumHostFragment
import com.yishenghuang.heartext.ui.reader.observeReadiumLocators
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.readium.r2.shared.publication.Locator
import java.io.File
import java.util.UUID

class ReadiumLocatorTest {
    @Test fun delayedHostAndLateSubscriberReceiveActualPdfNavigation() = runBlocking<Unit> {
        check(BuildConfig.APPLICATION_ID.endsWith(".validation"))
        val app = ApplicationProvider.getApplicationContext<HearTextApp>()
        val id = "pdf-locator-${UUID.randomUUID()}"
        val file = File(app.cacheDir, "$id.pdf")
        val pdf = PdfDocument()
        try {
            repeat(3) { index ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(300, 400, index + 1).create())
                page.canvas.drawText("Page ${index + 1}", 30f, 50f, android.graphics.Paint())
                pdf.finishPage(page)
            }
            file.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
        val legacy = """{"href":"publication.pdf","type":"application/pdf","locations":{"fragments":["page=3"],"position":3,"totalProgression":0.6666666666666666}}"""
        val session = app.container.readerSessions.open(id, file.path, legacy).getOrThrow()
        assertEquals(2, session.initialLocator!!.locations.position)
        val migrated = PdfLocatorCodec.restore(session.publication, legacy)!!
        assertEquals(2, migrated.locations.position)
        assertEquals(listOf("page=2"), migrated.locations.fragments)
        val encoded = PdfLocatorCodec.encode(migrated)
        assertEquals(migrated, PdfLocatorCodec.restore(session.publication, encoded))
        assertNull(PdfLocatorCodec.restore(session.publication, "broken"))
        var reopened: com.yishenghuang.heartext.readium.ReaderSession? = null
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val received = mutableListOf<Locator>()
                var observer: AutoCloseable? = null
                var containerId = 0
                scenario.onActivity { activity ->
                    val container = FragmentContainerView(activity).apply { this.id = View.generateViewId() }
                    containerId = container.id
                    activity.setContentView(container)
                    observer = activity.supportFragmentManager.observeReadiumLocators(id, scope) { received += it }
                }
                // Longer than the removed one-shot 300 ms lookup.
                delay(500)
                scenario.onActivity {
                    it.supportFragmentManager.beginTransaction()
                        .add(containerId, ReadiumHostFragment.newInstance(id), id).commitNow()
                }
                await(scenario) { received.isNotEmpty() }
                var host: ReadiumHostFragment? = null
                var rootView: View? = null
                scenario.onActivity {
                    host = it.supportFragmentManager.findFragmentByTag(id) as ReadiumHostFragment
                    rootView = it.window.decorView
                }
                await(scenario) { visiblePdfPage(rootView!!) == 1 }
                await(scenario) {
                    val current = received.last()
                    val target = current.copy(locations = current.locations.copy(fragments = listOf("page=3"), position = 3,
                        progression = 2.0 / 3.0, totalProgression = 2.0 / 3.0))
                    host!!.go(target)
                }
                await(scenario) { received.lastOrNull()?.locations?.position == 3 && visiblePdfPage(rootView!!) == 2 }
                scenario.onActivity { assertEquals(2, visiblePdfPage(it.window.decorView)) }
                scenario.onActivity {
                    observer!!.close()
                    received.clear()
                    observer = it.supportFragmentManager.observeReadiumLocators(id, scope) { locator -> received += locator }
                }
                await(scenario) { received.lastOrNull()?.locations?.position == 3 && visiblePdfPage(rootView!!) == 2 }
                scenario.onActivity { assertEquals(2, visiblePdfPage(it.window.decorView)) }
                scenario.onActivity { activity ->
                    val host = activity.supportFragmentManager.findFragmentByTag(id)!!
                    val saved = activity.supportFragmentManager.saveFragmentInstanceState(host)
                    activity.supportFragmentManager.beginTransaction().remove(host).commitNow()
                    received.clear()
                    val restored = ReadiumHostFragment.newInstance(id).apply { setInitialSavedState(saved) }
                    activity.supportFragmentManager.beginTransaction().add(containerId, restored, id).commitNow()
                }
                await(scenario) { received.lastOrNull()?.locations?.position == 3 && visiblePdfPage(rootView!!) == 2 }
                scenario.onActivity { assertEquals(2, visiblePdfPage(it.window.decorView)) }
                var savedWithoutSession: androidx.fragment.app.Fragment.SavedState? = null
                scenario.onActivity { activity ->
                    val currentHost = activity.supportFragmentManager.findFragmentByTag(id)!!
                    savedWithoutSession = activity.supportFragmentManager.saveFragmentInstanceState(currentHost)
                    activity.supportFragmentManager.beginTransaction().remove(currentHost).commitNow()
                    received.clear()
                }
                app.container.readerSessions.close(session)
                assertNull(app.container.readerSessions[id])
                scenario.onActivity { activity ->
                    val restored = ReadiumHostFragment.newInstance(id).apply { setInitialSavedState(savedWithoutSession) }
                    activity.supportFragmentManager.beginTransaction().add(containerId, restored, id).commitNow()
                    assertFalse(restored.bindAvailableSession())
                    val waitingState = activity.supportFragmentManager.saveFragmentInstanceState(restored)
                    activity.supportFragmentManager.beginTransaction().remove(restored).commitNow()
                    val waitingAgain = ReadiumHostFragment.newInstance(id).apply { setInitialSavedState(waitingState) }
                    activity.supportFragmentManager.beginTransaction().add(containerId, waitingAgain, id).commitNow()
                    assertFalse(waitingAgain.bindAvailableSession())
                }
                // The fragment snapshot is newer than this persisted second-page locator.
                reopened = app.container.readerSessions.open(id, file.path, encoded).getOrThrow()
                scenario.onActivity { activity ->
                    val restored = activity.supportFragmentManager.findFragmentByTag(id) as ReadiumHostFragment
                    assertTrue(restored.bindAvailableSession())
                    assertTrue(restored.bindAvailableSession()) // Recomposition must not recreate a live navigator.
                }
                await(scenario) { received.lastOrNull()?.locations?.position == 3 && visiblePdfPage(rootView!!) == 2 }
                scenario.onActivity { observer!!.close() }
            }
        } finally {
            scope.cancel()
            reopened?.let { app.container.readerSessions.close(it) }
            app.container.readerSessions.close(session)
            file.delete()
        }
    }

    private fun visiblePdfPage(view: View): Int? {
        // The renderer is a runtime-only Readium dependency. Inspect its public
        // currentPage getter to verify the rendered page independently of Locator.
        if (view.javaClass.name == "com.github.barteksc.pdfviewer.PDFView") {
            val count = view.javaClass.getMethod("getPageCount").invoke(view) as Int
            return if (count > 0) view.javaClass.getMethod("getCurrentPage").invoke(view) as Int else null
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            visiblePdfPage(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun await(scenario: ActivityScenario<MainActivity>, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            var satisfied = false
            scenario.onActivity { satisfied = condition() }
            if (satisfied) return
            SystemClock.sleep(20)
        }
        fail("PDF locator did not reach the expected position")
    }
}
