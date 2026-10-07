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
        sync.deleteRemoteBook(owned)
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
