package com.yishenghuang.heartext

import android.graphics.pdf.PdfDocument
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yishenghuang.heartext.data.*
import com.yishenghuang.heartext.network.*
import com.yishenghuang.heartext.readium.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class ReaderLifecycleTest {
    private lateinit var app: HearTextApp
    private lateinit var root: File
    private lateinit var db: AppDatabase
    private lateinit var books: BookRepository
    private lateinit var sessions: ReaderSessionRepository
    private lateinit var annotations: AnnotationRepository
    private val models = ViewModelStore()
    private val gate = CountDownLatch(1)
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val persistence = CoroutineScope(SupervisorJob() + dispatcher)

    @Before fun setUp() {
        check(BuildConfig.APPLICATION_ID.endsWith(".validation"))
        app = ApplicationProvider.getApplicationContext()
        root = File(app.cacheDir, "reader-lifecycle-${UUID.randomUUID()}").apply { mkdirs() }
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        books = BookRepository(app, db.bookDao(), CoverStore(app))
        sessions = ReaderSessionRepository(app, ReadiumFacade(app))
        val auth = object : SessionTokenProvider {
            override val isSignedIn = false
            override val sessionKey: String? = null
            override suspend fun getToken(forceRefresh: Boolean): String = error("No network in this test")
        }
        annotations = AnnotationRepository(db.annotationDao(), HearTextApi(auth), auth, db.bookDao())
        persistence.launch { gate.await() }
    }
    @After fun tearDown() = runBlocking {
        gate.countDown()
        withContext(Dispatchers.Main) { models.clear() }
        persistence.coroutineContext[Job]!!.children.toList().joinAll()
        persistence.cancel(); dispatcher.close()
        sessions.closeAll(); db.close(); root.deleteRecursively()
        Unit
    }
    private suspend fun reader(id: String): ReaderViewModel = withContext(Dispatchers.Main) {
        ReaderViewModel(id, books, app.container.readerPreferences, app.container.playbackCoordinator,
            sessions, app.container.fontStore, annotations, app, persistence).also { models.put(id, it) }
    }
    private fun pdf(): File {
        val file = File(root, "fixture.pdf")
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(200, 200, 1).create())
            document.finishPage(page)
            file.outputStream().use(document::writeTo)
        } finally { document.close() }
        return file
    }

    @Test fun queuedPageEventsSurviveImmediateViewModelClearAndKeepNewestPosition() = runBlocking {
        val file = File(root, "fixture.txt").apply { writeText("Chapter 1\n" + "Local reader content. ".repeat(100)) }
        db.bookDao().upsert(BookEntity("text", "Title", "Author", BookFormat.TXT, file.path))
        val oldTimestamp = books.captureProgressTimestamp()
        val vm = reader("text")
        withTimeout(5000) { vm.sessionReady.first { it } }
        withContext(Dispatchers.Main) {
            vm.onEnginePageChanged(0, 3, 10)
            vm.onEnginePageChanged(0, 4, 10)
            models.clear()
        }
        assertEquals(0, db.bookDao().getBook("text")!!.lastOffset)
        gate.countDown()
        withTimeout(5000) { while (db.bookDao().getBook("text")!!.lastOffset != 4) delay(10) }
        books.updateProgress("text", 0, 1, 1f, updatedAt = oldTimestamp)
        assertEquals(4, db.bookDao().getBook("text")!!.lastOffset)
    }

    @Test fun clearingPdfReaderReleasesItsPublicationOutsideCancelledScope() = runBlocking {
        db.bookDao().upsert(BookEntity("pdf", "PDF", "", BookFormat.PDF, pdf().path))
        val vm = reader("pdf")
        withTimeout(10000) { vm.sessionReady.first { it } }
        assertTrue(sessions["pdf"] is ReaderSession.Pdf)
        val locator = """{"href":"fixture.pdf","type":"application/pdf","locations":{"position":1}}"""
        withContext(Dispatchers.Main) {
            vm.persistLocator(locator, 75f)
            vm.persistProgress(offset = 0)
            models.clear()
        }
        assertNotNull(sessions["pdf"])
        gate.countDown()
        withTimeout(5000) { while (sessions["pdf"] != null) delay(10) }
        assertNull(sessions["pdf"])
        assertEquals(locator, db.bookDao().getBook("pdf")!!.locatorJson)
        assertEquals(75f, db.bookDao().getBook("pdf")!!.progressPercent)
    }

    @Test fun failedDocumentOpenCannotResetSavedProgress() = runBlocking {
        val file = File(root, "broken.txt").apply { writeBytes(byteArrayOf(0, 1, 2)) }
        db.bookDao().upsert(BookEntity("broken", "Broken", "", BookFormat.TXT, file.path,
            lastOffset = 7, progressPercent = 50f))
        val vm = reader("broken")
        withTimeout(5000) { vm.loading.first { !it } }
        assertFalse(vm.sessionReady.value)
        withContext(Dispatchers.Main) { vm.persistProgress(offset = 0); models.clear() }
        gate.countDown()
        persistence.coroutineContext[Job]!!.children.toList().joinAll()
        assertEquals(7, db.bookDao().getBook("broken")!!.lastOffset)
        assertEquals(50f, db.bookDao().getBook("broken")!!.progressPercent)
    }

    @Test fun sharedPublicationStaysOpenUntilLastOwnerAndStaleCloseCannotCloseNewSession() = runBlocking {
        val path = pdf().path
        val first = sessions.open("pdf", path, null).getOrThrow()
        val second = sessions.open("pdf", path, null).getOrThrow()
        assertSame(first, second)
        sessions.close(first)
        assertSame(second, sessions["pdf"])
        sessions.close(second)
        assertNull(sessions["pdf"])
        val reopened = sessions.open("pdf", path, null).getOrThrow()
        sessions.close(first)
        assertSame(reopened, sessions["pdf"])
        sessions.close(reopened)
    }
}
