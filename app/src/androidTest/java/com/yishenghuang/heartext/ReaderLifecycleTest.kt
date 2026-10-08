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

    @Test fun bookmarkFailureClearsBusyAndRapidRetryCreatesOnlyOneBookmark() = runBlocking {
        val file = File(root, "bookmark.txt").apply { writeText("A local reading fixture.") }
        db.bookDao().upsert(BookEntity("bookmark", "Title", "Author", BookFormat.TXT, file.path))
        val dao = db.annotationDao()
        var fail = true
        val release = CompletableDeferred<Unit>()
        val intercepted = object : AnnotationDao by dao {
            override suspend fun listForBook(bookId: String): List<AnnotationEntity> {
                if (fail) throw java.io.IOException("private document path")
                release.await()
                return dao.listForBook(bookId)
            }
        }
        val auth = object : SessionTokenProvider {
            override val isSignedIn = false
            override val sessionKey: String? = null
            override suspend fun getToken(forceRefresh: Boolean): String = error("No network")
        }
        annotations = AnnotationRepository(intercepted, HearTextApi(auth), auth, db.bookDao())
        val vm = reader("bookmark")
        withTimeout(5000) { vm.sessionReady.first { it } }
        withContext(Dispatchers.Main) { repeat(20) { vm.addBookmark(0) } }
        withTimeout(5000) { vm.bookmarkBusy.first { !it } }
        assertEquals(app.getString(R.string.error_bookmark_save), vm.bookmarkToast.value)
        assertTrue(dao.listForBook("bookmark").isEmpty())
        fail = false
        withContext(Dispatchers.Main) { repeat(20) { vm.addBookmark(0) } }
        assertTrue(vm.bookmarkBusy.value)
        release.complete(Unit)
        withTimeout(5000) { vm.bookmarkBusy.first { !it } }
        assertEquals(1, dao.listForBook("bookmark").size)
        assertEquals(app.getString(R.string.toast_bookmark_added), vm.bookmarkToast.value)
        withContext(Dispatchers.Main) { vm.addBookmark(0) }
        withTimeout(5000) { vm.bookmarkBusy.first { !it } }
        assertTrue(dao.listForBook("bookmark").isEmpty())
    }

    @Test fun invalidTextReportsLocalizedErrorWithoutUnderlyingPath() = runBlocking {
        val file = File(root, "private-title.txt").apply { writeBytes(byteArrayOf(0, 1, 2, 3)) }
        db.bookDao().upsert(BookEntity("invalid", "Private title", "Author", BookFormat.TXT, file.path))
        val vm = reader("invalid")
        withTimeout(5000) { vm.loading.first { !it } }
        assertEquals(app.getString(R.string.error_load_chapters), vm.error.value)
        assertFalse(vm.sessionReady.value)
        withContext(Dispatchers.Main) { vm.addBookmark(0) }
        assertTrue(db.annotationDao().listForBook("invalid").isEmpty())
    }

    @Test fun queuedPageEventsSurviveImmediateViewModelClearAndKeepNewestPosition() = runBlocking {
        val file = File(root, "fixture.txt").apply { writeText("Chapter 1\n" + "Local reader content. ".repeat(100)) }
        db.bookDao().upsert(BookEntity("text", "Title", "Author", BookFormat.TXT, file.path))
        val oldTimestamp = books.captureProgressTimestamp()
        val vm = reader("text")
        withTimeout(5000) { vm.sessionReady.first { it } }
        withContext(Dispatchers.Main) {
            vm.onEnginePageChanged(0, 3, 10, 120)
            vm.onEnginePageChanged(0, 4, 10, 240)
            models.clear()
        }
        assertEquals(0, db.bookDao().getBook("text")!!.lastOffset)
        gate.countDown()
        withTimeout(5000) { while (db.bookDao().getBook("text")!!.lastOffset != 4) delay(10) }
        books.updateProgress("text", 0, 1, 1f, updatedAt = oldTimestamp)
        assertEquals(4, db.bookDao().getBook("text")!!.lastOffset)
        assertEquals(TextPosition(0, 240), TextPosition.decode(db.bookDao().getBook("text")!!.locatorJson))
        val reopened = reader("text")
        withTimeout(5000) { reopened.sessionReady.first { it } }
        assertEquals(TextPosition(0, 240), reopened.textPosition)
        assertNull(TextPosition.decode("""{"locations":{"position":4}}"""))
        assertNull(TextPosition.decode("""{"heartextTextPositionVersion":2,"chapter":0,"character":240}"""))
        assertNull(TextPosition.decode(TextPosition(0, -1).encode()))

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
        // Publication close and Room persistence are independent coroutine jobs.
        withTimeout(5000) { persistence.coroutineContext[Job]!!.children.toList().joinAll() }
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
        val latest = """{"href":"publication.pdf","type":"application/pdf","title":"Latest resume position","heartextPdfiumLocatorVersion":1,"locations":{"position":1}}"""
        val second = sessions.open("pdf", path, latest).getOrThrow()
        assertSame(first, second)
        assertEquals("Latest resume position", second.initialLocator!!.title)
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
