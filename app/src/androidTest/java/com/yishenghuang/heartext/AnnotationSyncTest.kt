package com.yishenghuang.heartext

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yishenghuang.heartext.data.*
import com.yishenghuang.heartext.network.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.time.Instant
import java.util.concurrent.TimeUnit

class AnnotationSyncTest {
    @get:Rule val server = MockWebServer()
    private lateinit var db: AppDatabase
    private lateinit var repository: AnnotationRepository
    private val auth = object : SessionTokenProvider {
        override val isSignedIn = true
        @Volatile override var sessionKey: String? = "account-a"
        override suspend fun getToken(forceRefresh: Boolean) = "fixture-token"
    }
    private val time = "2026-10-07T12:00:00.500Z"
    private val annotation = AnnotationEntity("local-note", "book", "client-note", "note",
        note = "Local", remoteId = "remote-note", remoteOwnerId = "account-a",
        clientUpdatedAt = Instant.parse(time).toEpochMilli())
    private fun remote(client: String = "client-note", note: String = "Remote", updated: String = time) =
        JSONObject().put("id", "remote-note").put("book_id", "remote-book")
            .put("client_annotation_id", client).put("type", "note").put("note", note)
            .put("client_updated_at", updated).toString()

    @Before fun setUp() = runBlocking {
        check(BuildConfig.APPLICATION_ID.endsWith(".validation"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        db.bookDao().upsert(BookEntity("book", "Title", "Author", BookFormat.TXT, "/fixture.txt",
            remoteBookId = "remote-book", remoteOwnerId = "account-a"))
        repository = AnnotationRepository(db.annotationDao(),
            HearTextApi(auth, OkHttpClient(), server.url("/").toString()), auth, db.bookDao())
    }
    @After fun tearDown() { db.close() }

    @Test fun characterBookmarkRoundTripsThroughExtrasAndCoexistsWithLegacyPageBookmarks() = runBlocking {
        val posted = java.util.concurrent.atomic.AtomicReference<JSONObject>()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "POST") {
                    val payload = JSONObject(request.body.readUtf8()).put("id", "remote-bookmark")
                    posted.set(payload)
                    return MockResponse().setBody(payload.toString())
                }
                return MockResponse().setBody("[${posted.get()}]")
            }
        }
        val created = repository.addBookmark("book", "remote-book", 2, 3, characterOffset = 1200)
        assertEquals(1200, posted.get().getJSONObject("extras").getJSONObject("heartext_text_position").getInt("character"))
        db.annotationDao().delete(created.id)
        repository.syncFromServer("book", "remote-book")
        val restored = db.annotationDao().listForBook("book").single()
        assertEquals(TextPosition(2, 1200), TextPosition.decode(restored.locatorJson))
        assertEquals(restored.id, repository.findBookmark("book", 2, 99, 1100..1300)!!.id)
        val legacy = restored.copy(id = "legacy", clientAnnotationId = "legacy", locatorJson = null, remoteId = null)
        val other = restored.copy(id = "other", clientAnnotationId = "other", locatorJson = TextPosition(2, 2400).encode(), remoteId = null)
        db.annotationDao().upsert(legacy)
        db.annotationDao().upsert(other)
        repository.dedupeBookmarks("book")
        assertEquals(3, db.annotationDao().listForBook("book").size)
        assertEquals("legacy", repository.findBookmark("book", 2, 3, 100..200)!!.id)
        assertNull(repository.findBookmark("book", 2, 99, 100..200))
    }

    @Test fun failedDeleteStaysHiddenAndRetriesWithoutResurrection() = runBlocking {
        db.annotationDao().upsert(annotation)
        server.enqueue(MockResponse().setResponseCode(503))
        repository.delete(annotation)
        assertTrue(db.annotationDao().listForBook("book").isEmpty())
        assertFalse(db.annotationDao().get(annotation.id)!!.deleteSynced)
        assertNull(db.annotationDao().get(annotation.id)!!.note)
        server.enqueue(MockResponse().setBody("[${remote()}]"))
        server.enqueue(MockResponse().setResponseCode(204))
        repository.syncFromServer("book", "remote-book")
        assertTrue(db.annotationDao().listForBook("book").isEmpty())
        assertTrue(db.annotationDao().get(annotation.id)!!.deleteSynced)
        assertEquals(3, server.requestCount)
    }

    @Test fun deletingDuringCreateCannotBeUndoneByLateResponse() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = if (request.method == "POST") {
                val client = JSONObject(request.body.readUtf8()).getString("client_annotation_id")
                MockResponse().setBody(remote(client)).setBodyDelay(400, TimeUnit.MILLISECONDS)
            } else MockResponse().setResponseCode(204)
        }
        val creation = async { repository.addNote("book", "remote-book", 0, "New note") }
        withContext(Dispatchers.IO) { assertEquals("POST", server.takeRequest(5, TimeUnit.SECONDS)?.method) }
        val local = db.annotationDao().listForBook("book").single()
        val deletion = async { repository.delete(local) }
        withTimeout(2000) { while (db.annotationDao().get(local.id)?.deleted != true) delay(10) }
        creation.await(); deletion.await()
        assertTrue(db.annotationDao().listForBook("book").isEmpty())
        assertTrue(db.annotationDao().get(local.id)!!.deleteSynced)
        assertEquals(2, server.requestCount)
    }

    @Test fun mergePreservesMillisecondsAndRejectsOlderRemoteContent() = runBlocking {
        db.annotationDao().upsert(annotation)
        server.enqueue(MockResponse().setBody("[${remote(updated = "2026-10-07T12:00:00.700Z")}]"))
        repository.syncFromServer("book", "remote-book")
        assertEquals("Remote", db.annotationDao().get(annotation.id)?.note)
        server.enqueue(MockResponse().setBody("[${remote(note = "Old", updated = "2026-10-07T12:00:00.600Z")}]"))
        repository.syncFromServer("book", "remote-book")
        assertEquals("Remote", db.annotationDao().get(annotation.id)?.note)
        assertEquals(Instant.parse("2026-10-07T12:00:00.700Z").toEpochMilli(), db.annotationDao().get(annotation.id)?.clientUpdatedAt)
    }

    @Test fun otherAccountCannotSyncOrDeleteRemoteAnnotations() = runBlocking {
        db.annotationDao().upsert(annotation)
        auth.sessionKey = "account-b"
        repository.syncFromServer("book", "remote-book")
        repository.delete(annotation)
        assertEquals(0, server.requestCount)
        assertTrue(db.annotationDao().get(annotation.id)!!.deleted)
    }

    @Test fun completeRemoteListPropagatesRemoteDeletionWithoutRecreatingIt() = runBlocking {
        db.annotationDao().upsert(annotation)
        server.enqueue(MockResponse().setBody("[]"))
        repository.syncFromServer("book", "remote-book")
        assertTrue(db.annotationDao().listForBook("book").isEmpty())
        assertTrue(db.annotationDao().get(annotation.id)!!.deleteSynced)
        assertEquals(1, server.requestCount)
    }

    @Test fun failedCreateIsRetriedByNextSyncUsingSameClientId() = runBlocking {
        val clients = mutableListOf<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "GET") return MockResponse().setBody("[]")
                val client = JSONObject(request.body.readUtf8()).getString("client_annotation_id")
                clients += client
                return if (clients.size == 1) MockResponse().setResponseCode(503)
                else MockResponse().setBody(remote(client))
            }
        }
        val created = repository.addNote("book", "remote-book", 0, "Saved locally")
        assertNull(db.annotationDao().get(created.id)!!.remoteId)
        repository.syncFromServer("book", "remote-book")
        assertEquals("remote-note", db.annotationDao().get(created.id)!!.remoteId)
        assertEquals(listOf(created.clientAnnotationId, created.clientAnnotationId), clients)
        assertEquals("Saved locally", db.annotationDao().get(created.id)!!.note)
        assertEquals(3, server.requestCount)
    }
}
