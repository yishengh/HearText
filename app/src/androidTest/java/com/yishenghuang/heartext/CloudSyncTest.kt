package com.yishenghuang.heartext

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yishenghuang.heartext.data.*
import com.yishenghuang.heartext.network.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

class CloudSyncTest {
    @get:Rule val server = MockWebServer()
    private lateinit var db: AppDatabase
    private lateinit var sync: CloudSyncRepository
    private val auth = object : SessionTokenProvider {
        override val isSignedIn = true
        @Volatile override var sessionKey: String? = "account-a"
        override suspend fun getToken(forceRefresh: Boolean) = "local-test-token"
    }
    private val book = BookEntity("local", "Title", "Author", BookFormat.TXT, "/fixture.txt")
    private val remote = """{"id":"remote","client_book_id":"local","title":"Title"}"""

    @Before fun setUp() {
        check(BuildConfig.APPLICATION_ID.endsWith(".validation"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        sync = CloudSyncRepository(db.bookDao(), HearTextApi(auth, OkHttpClient(), server.url("/").toString()), auth, CoverStore(context))
    }
    @After fun tearDown() { db.close() }

    @Test fun aggregateSyncReportsPendingDeletionsAndStillAttemptsPull() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = BookRepository(context, db.bookDao(), CoverStore(context), sync)
        db.bookDao().queueDeletion(PendingBookDeletion("account-a", "local", "remote"))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody("[]"))
        server.enqueue(MockResponse().setBody("[]"))
        assertTrue(runCatching { repository.syncOnLogin() }.exceptionOrNull() is SyncIncompleteException)
        assertEquals(3, server.requestCount)
        assertEquals("DELETE", server.takeRequest().method)
        assertEquals("/v1/books", server.takeRequest().path)
        assertEquals("/v1/progress", server.takeRequest().path)
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setBody("[]"))
        server.enqueue(MockResponse().setBody("[]"))
        repository.syncOnLogin()
        assertTrue(db.bookDao().pendingDeletions("account-a").isEmpty())
        assertEquals(6, server.requestCount)
    }

    @Test fun deletionSurvivesFailureAndOnlyOriginalAccountCanRetry() = runBlocking {
        val owned = book.copy(remoteBookId = "remote", remoteOwnerId = "account-a")
        db.bookDao().upsert(owned)
        db.bookDao().removeAndQueueDeletion(book.id)
        assertNull(db.bookDao().getBook(book.id))
        server.enqueue(MockResponse().setResponseCode(503))
        sync.flushPendingDeletions()
        assertEquals(1, db.bookDao().pendingDeletions("account-a").size)
        auth.sessionKey = "account-b"
        sync.flushPendingDeletions()
        assertEquals(1, server.requestCount)
        auth.sessionKey = "account-a"
        server.enqueue(MockResponse().setResponseCode(404))
        sync.flushPendingDeletions()
        assertTrue(db.bookDao().pendingDeletions("account-a").isEmpty())
        assertEquals(2, server.requestCount)
        repeat(2) { assertEquals("DELETE", server.takeRequest().method) }
    }

    @Test fun registrationFinishesBeforeDeletionAndLostRegistrationCanBeResolved() = runBlocking {
        db.bookDao().upsert(book)
        server.enqueue(MockResponse().setBody("[]"))
        server.enqueue(MockResponse().setBody(remote).setBodyDelay(250, TimeUnit.MILLISECONDS))
        server.enqueue(MockResponse().setResponseCode(503))
        val registration = async { sync.ensureRemoteBook(book) }
        withContext(Dispatchers.IO) {
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals("POST", server.takeRequest(5, TimeUnit.SECONDS)!!.method)
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = BookRepository(context, db.bookDao(), CoverStore(context), sync, db.annotationDao())
        val deletion = async { repo.deleteBook(book.id) }
        registration.await()
        assertEquals(BookDeletionResult.REMOTE_PENDING, deletion.await())
        assertNull(db.bookDao().getBook(book.id))
        assertEquals("remote", db.bookDao().pendingDeletions("account-a").single().remoteId)
        server.enqueue(MockResponse().setResponseCode(204))
        sync.flushPendingDeletions()
        // Simulate process loss after POST began, before its response was bound locally.
        db.bookDao().upsert(book.copy(remoteOwnerId = "account-a"))
        db.bookDao().removeAndQueueDeletion(book.id)
        server.enqueue(MockResponse().setBody("[]"))
        sync.flushPendingDeletions()
        assertEquals(1, db.bookDao().pendingDeletions("account-a").size)
        server.enqueue(MockResponse().setBody("[$remote]"))
        server.enqueue(MockResponse().setResponseCode(204))
        sync.flushPendingDeletions()
        assertTrue(db.bookDao().pendingDeletions("account-a").isEmpty())
        assertEquals(7, server.requestCount)
    }

    @Test fun laterShelfReplacementSupersedesPendingDeletion() = runBlocking {
        val owned = book.copy(remoteBookId = "remote", remoteOwnerId = "account-a")
        db.bookDao().upsert(owned)
        db.bookDao().removeAndQueueDeletion(book.id)
        db.bookDao().upsert(owned.copy(id = "replacement"))
        sync.flushPendingDeletions()
        assertEquals(0, server.requestCount)
        assertTrue(db.bookDao().pendingDeletions("account-a").isEmpty())
        assertNotNull(db.bookDao().getBook("replacement"))
    }

    @Test fun concurrentProgressPushesSendLatestLocatorOnceAndPullRestoresIt() = runBlocking {
        val local = book.copy(remoteBookId = "remote", remoteOwnerId = "account-a",
            lastChapterIndex = 2, lastOffset = 4, progressPercent = 40f, progressUpdatedAt = 5000,
            locatorJson = TextPosition(2, 1200).encode())
        db.bookDao().upsert(local)
        val posted = java.util.concurrent.atomic.AtomicReference<org.json.JSONObject>()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                if (request.method == "PUT") {
                    val payload = org.json.JSONObject(request.body.readUtf8()).put("book_id", "remote")
                    posted.set(payload)
                    return MockResponse().setBody(payload.toString())
                }
                return if (request.path == "/v1/books") MockResponse().setBody("[$remote]")
                else MockResponse().setBody("[${posted.get()}]")
            }
        }
        (1..12).map { async { sync.pushProgress(local.copy(lastOffset = 0, locatorJson = null)) } }.awaitAll()
        assertEquals(1, server.requestCount)
        assertEquals(4, posted.get().getInt("position"))
        assertEquals(1200, posted.get().getJSONObject("extras").getJSONObject("heartext_locator").getInt("character"))
        db.bookDao().upsert(local.copy(progressUpdatedAt = 1, lastOffset = 0, locatorJson = null))
        sync.pullAndMergeProgress()
        val restored = db.bookDao().getBook(local.id)!!
        assertEquals(TextPosition(2, 1200), TextPosition.decode(restored.locatorJson))
        assertEquals(4, restored.lastOffset)
    }

    @Test fun failedProgressPushRemainsRetryable() = runBlocking {
        val local = book.copy(remoteBookId = "remote", remoteOwnerId = "account-a", progressUpdatedAt = 5000,
            locatorJson = TextPosition(0, 50).encode())
        db.bookDao().upsert(local)
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(runCatching { sync.pushProgress(local) }.isFailure)
        server.enqueue(MockResponse().setBody("""{"book_id":"remote","client_updated_at":"1970-01-01T00:00:05Z"}"""))
        sync.pushProgress(local)
        assertEquals(2, server.requestCount)
        assertEquals(local.locatorJson, db.bookDao().getBook(local.id)!!.locatorJson)
    }

    @Test fun delayedRegistrationDoesNotOverwriteConcurrentLocalChanges() = runBlocking {
        db.bookDao().upsert(book)
        server.enqueue(MockResponse().setBody("[$remote]").setBodyDelay(300, TimeUnit.MILLISECONDS))
        val registration = async { sync.ensureRemoteBook(book) }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
        db.bookDao().updateProgress(book.id, 2, 123, 60f, 5000, "locator", null)
        db.bookDao().updateDescription(book.id, "User description")
        db.bookDao().updateCover(book.id, "/user.jpg", CoverSource.USER)
        val result = registration.await()
        assertEquals(123, result.lastOffset)
        assertEquals("User description", result.description)
        assertEquals(CoverSource.USER, result.coverSource)
        assertEquals("account-a", result.remoteOwnerId)
        assertEquals("remote", result.remoteBookId)
    }

    @Test fun failedListingNeverCreatesAndConcurrentRegistrationCreatesOnlyOnce() = runBlocking {
        db.bookDao().upsert(book)
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(runCatching { sync.ensureRemoteBook(book) }.isFailure)
        assertEquals(1, server.requestCount)
        server.enqueue(MockResponse().setBody("[]"))
        server.enqueue(MockResponse().setBody(remote))
        coroutineScope { repeat(2) { launch { sync.ensureRemoteBook(book) } } }
        assertEquals(3, server.requestCount)
        assertEquals("remote", db.bookDao().getBook(book.id)?.remoteBookId)
    }

    @Test fun otherAccountCannotUploadOrDeleteOwnedBookAndOldProgressCannotOverwrite() = runBlocking {
        val owned = book.copy(remoteOwnerId = "account-a", remoteBookId = "remote", progressUpdatedAt = 5000, lastOffset = 99)
        db.bookDao().upsert(owned)
        auth.sessionKey = "account-b"
        sync.pushProgress(owned)
        db.bookDao().queueDeletion(PendingBookDeletion("account-a", book.id, "remote"))
        sync.flushPendingDeletions()
        assertEquals(0, server.requestCount)
        db.bookDao().mergeRemoteProgress(book.id, "account-b", 0, 1, 1f, 6000)
        db.bookDao().mergeRemoteProgress(book.id, "account-a", 0, 2, 2f, 4000)
        assertEquals(99, db.bookDao().getBook(book.id)?.lastOffset)
        db.bookDao().mergeRemoteProgress(book.id, "account-a", 1, 3, 3f, 6000)
        assertEquals(3, db.bookDao().getBook(book.id)?.lastOffset)
    }

    @Test fun legacyRemoteIdRequiresProofOfOwnership() = runBlocking {
        val legacy = book.copy(remoteBookId = "unverified", progressUpdatedAt = 5000)
        db.bookDao().upsert(legacy)
        server.enqueue(MockResponse().setBody("[]"))
        sync.pushProgress(legacy)
        assertEquals(1, server.requestCount)
        assertNull(db.bookDao().getBook(book.id)?.remoteOwnerId)
    }
}
