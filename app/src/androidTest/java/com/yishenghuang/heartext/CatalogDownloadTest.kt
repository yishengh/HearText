package com.yishenghuang.heartext

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yishenghuang.heartext.data.*
import com.yishenghuang.heartext.network.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

class CatalogDownloadTest {
    @get:Rule val server = MockWebServer()
    private lateinit var root: File
    private lateinit var db: AppDatabase
    private lateinit var repository: CatalogRepository
    private lateinit var epub: ByteArray
    private val auth = object : SessionTokenProvider {
        override val isSignedIn = true
        @Volatile override var sessionKey: String? = "account-a"
        override suspend fun getToken(forceRefresh: Boolean) = "fixture-token"
    }
    private val catalog = ApiCatalogBook("catalog", "fixture", "id", "Title", "Author", "en",
        null, "Description", "public domain", "epub", 0, 0, false, 1, 0, 0)

    @Before fun setUp() {
        check(BuildConfig.APPLICATION_ID.endsWith(".validation"))
        val app = ApplicationProvider.getApplicationContext<Context>()
        root = File(app.cacheDir, "catalog-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getFilesDir() = root }
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        repository = CatalogRepository(context, HearTextApi(auth, OkHttpClient(), server.url("/").toString()),
            auth, db.bookDao(), CoverStore(context))
        epub = app.assets.open("samples/alice_in_wonderland.epub").use { it.readBytes() }
    }
    @After fun tearDown() { db.close(); root.deleteRecursively() }
    private fun enqueueDownload(remoteId: String = "remote-a") {
        server.enqueue(MockResponse().setBody(Buffer().write(epub)))
        server.enqueue(MockResponse().setBody("""{"book":{"id":"$remoteId","client_book_id":"catalog-local","title":"Title","author":"Author"}}"""))
    }

    @Test fun repeatedDownloadRetainsProgressAnnotationsAndUserCover() = runBlocking {
        enqueueDownload()
        val first = repository.downloadAndShelf(catalog)
        val userCover = File(root, "user.jpg").apply { writeText("user image") }
        db.bookDao().updateCover(first.id, userCover.path, CoverSource.USER)
        db.bookDao().updateProgress(first.id, 1, 12, 25f, 1234, "locator", null)
        db.bookDao().updateDescription(first.id, "Edited description")
        db.annotationDao().upsert(AnnotationEntity("note", first.id, "note", "note", note = "Keep"))
        enqueueDownload()
        val second = repository.downloadAndShelf(catalog)
        assertEquals(first.id, second.id)
        assertEquals(first.addedAt, second.addedAt)
        assertEquals(12, second.lastOffset)
        assertEquals(1234L, second.progressUpdatedAt)
        assertEquals("locator", second.locatorJson)
        assertEquals("Edited description", second.description)
        assertEquals(userCover.path, second.coverPath)
        assertEquals("user image", userCover.readText())
        assertEquals(1, db.annotationDao().listForBook(first.id).size)
        assertFalse(File(first.filePath).exists())
        assertTrue(File(second.filePath).exists())
        assertEquals(1, File(root, "catalog").listFiles()!!.size)
    }

    @Test fun anotherAccountWithSameClientIdDoesNotReplaceExistingBook() = runBlocking {
        enqueueDownload()
        val first = repository.downloadAndShelf(catalog)
        auth.sessionKey = "account-b"
        enqueueDownload("remote-b")
        val second = repository.downloadAndShelf(catalog)
        assertNotEquals(first.id, second.id)
        assertEquals(first, db.bookDao().getBook(first.id))
        assertEquals("account-b", second.remoteOwnerId)
        assertTrue(File(first.filePath).exists())
        assertEquals(2, db.bookDao().count())
    }

    @Test fun corruptAndCancelledDownloadsLeaveNoBookOrStagedFile() = runBlocking {
        server.enqueue(MockResponse().setBody("not an epub"))
        assertTrue(runCatching { repository.downloadAndShelf(catalog) }.isFailure)
        assertEquals(1, server.requestCount) // Validation must precede POST /shelf.
        assertEquals(0, db.bookDao().count())
        assertTrue(File(root, "catalog").listFiles()!!.isEmpty())
        server.takeRequest()
        server.enqueue(MockResponse().setBody(Buffer().write(epub)).throttleBody(128, 30, TimeUnit.SECONDS))
        val job = launch { repository.downloadAndShelf(catalog) }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
        withTimeout(2000) { job.cancelAndJoin() }
        assertEquals(0, db.bookDao().count())
        assertTrue(File(root, "catalog").listFiles()!!.isEmpty())
    }

    @Test fun accountSwitchWhileShelvingDoesNotCommitOldAccountBook() = runBlocking {
        server.enqueue(MockResponse().setBody(Buffer().write(epub)))
        server.enqueue(MockResponse().setBody("""{"book":{"id":"remote-a","client_book_id":"catalog-local","title":"Title"}}""")
            .setBodyDelay(500, TimeUnit.MILLISECONDS))
        val result = async { runCatching { repository.downloadAndShelf(catalog) }.exceptionOrNull() }
        withContext(Dispatchers.IO) {
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals("POST", server.takeRequest(5, TimeUnit.SECONDS)?.method)
        }
        auth.sessionKey = "account-b"
        assertTrue(result.await() is CancellationException)
        assertEquals(0, db.bookDao().count())
        assertTrue(File(root, "catalog").listFiles()!!.isEmpty())
    }
}
