package com.yishenghuang.heartext

import com.yishenghuang.heartext.network.ApiHttpException
import com.yishenghuang.heartext.network.HearTextApi
import com.yishenghuang.heartext.network.SessionTokenProvider
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

class HearTextApiTest {
    @get:Rule val server = MockWebServer()
    @get:Rule val folder = TemporaryFolder()
    private class Tokens : SessionTokenProvider {
        override val isSignedIn = true
        @Volatile override var sessionKey: String? = "session-a"
        val refreshes = mutableListOf<Boolean>()
        override suspend fun getToken(forceRefresh: Boolean): String {
            refreshes += forceRefresh
            return if (forceRefresh) "fresh-test-token" else "old-test-token"
        }
    }
    private val tokens = Tokens()
    private fun api() = HearTextApi(tokens, OkHttpClient(), server.url("/").toString())

    @Test fun unavailableWithZeroDelayDoesNotReplayWriteInsideOkHttp() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).addHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val failure = runCatching { api().updateMe(displayName = "Local test") }.exceptionOrNull()
        assertTrue(failure is ApiHttpException)
        assertEquals(503, (failure as ApiHttpException).code)
        assertEquals(0L, failure.retryAfterMillis)
        assertEquals(1, server.requestCount)
        assertEquals("PATCH", server.takeRequest().method)
        assertFalse(tokens.refreshes.contains(true))
    }

    @Test fun timeoutResponseDoesNotReplayWriteInsideOkHttp() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(408))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val failure = runCatching { api().updateMe(displayName = "Local test") }.exceptionOrNull()
        assertEquals(408, (failure as ApiHttpException).code)
        assertEquals(1, server.requestCount)
    }

    @Test fun overflowingRetryAfterDoesNotCrashOkHttpOrReplayDownload() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).addHeader("Retry-After", "99999999999999999999"))
        val file = folder.newFile("existing.zip").apply { writeText("keep") }
        val failure = runCatching { api().downloadOfflineVoice("voice", file) }.exceptionOrNull()
        assertEquals(503, (failure as ApiHttpException).code)
        assertTrue(failure.retryAfterMillis!! > 0)
        assertEquals("keep", file.readText())
        assertEquals(1, server.requestCount)
    }

    @Test fun rateLimitBlocksRepeatedDownloadsWithoutRefreshingOrChangingFiles() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "60"))
        val api = api()
        val file = folder.newFile("limited.zip").apply { writeText("keep") }
        repeat(3) {
            val failure = runCatching { api.downloadOfflineVoice("voice", file) }.exceptionOrNull()
            assertTrue(failure is ApiHttpException)
            assertEquals(429, (failure as ApiHttpException).code)
            assertTrue(failure.retryAfterMillis!! > 0)
        }
        assertEquals(1, server.requestCount)
        assertFalse(tokens.refreshes.contains(true))
        assertEquals("keep", file.readText())
        assertEquals(1, folder.root.list()!!.size)
    }

    @Test fun accountSwitchDuringDownloadPreservesInstalledFile() = runBlocking {
        server.enqueue(MockResponse().setBody("replacement"))
        val file = folder.newFile("voice.zip").apply { writeText("installed") }
        val failure = runCatching {
            api().downloadOfflineVoice("voice", file) { _, _ -> tokens.sessionKey = "session-b" }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals("installed", file.readText())
        assertEquals(1, folder.root.list()!!.size)
    }

    @Test fun accountSwitchBeforeUnauthorizedResponseDoesNotRetryAsNewUser() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("expired")
            .setBodyDelay(300, TimeUnit.MILLISECONDS))
        val api = api()
        val result = async { runCatching { api.listBooks() }.exceptionOrNull() }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
        tokens.sessionKey = "session-b"
        assertTrue(result.await() is CancellationException)
        assertEquals(listOf(false), tokens.refreshes)
        assertEquals(1, server.requestCount)
    }

    @Test fun unauthorizedDownloadRefreshesOnceThenCommits() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("expired"))
        server.enqueue(MockResponse().setBody("epub fixture"))
        val file = folder.newFile("book.epub")
        api().downloadCatalogBook("book", file)
        assertEquals("epub fixture", file.readText())
        assertEquals(listOf(false, true), tokens.refreshes)
        assertEquals("Bearer old-test-token", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer fresh-test-token", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun repeatedUnauthorizedDoesNotLoopOrLeakServerBody() = runBlocking {
        repeat(2) { server.enqueue(MockResponse().setResponseCode(401).setBody("private server detail")) }
        val file = folder.newFile("book.epub").apply { writeText("keep") }
        val failure = runCatching { api().downloadCatalogBook("book", file) }.exceptionOrNull()
        assertTrue(failure is ApiHttpException)
        assertEquals("HTTP 401", failure?.message)
        assertEquals(2, server.requestCount)
        assertEquals("keep", file.readText())
    }

    @Test fun cancellingStalledHeadersClosesCallPromptly() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val api = api()
        val job = launch { api.health() }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
        withTimeout(2_000) { job.cancelAndJoin() }
        assertTrue(job.isCancelled)
    }

    @Test fun cancellingStalledBodyPreservesFileAndDeletesPartialDownload() = runBlocking {
        server.enqueue(MockResponse().setBody("x".repeat(32_000)).throttleBody(1024, 30, TimeUnit.SECONDS))
        val file = folder.newFile("voice.zip").apply { writeText("installed") }
        val started = CompletableDeferred<Unit>()
        val api = api()
        val job = launch { api.downloadOfflineVoice("voice", file) { _, _ -> started.complete(Unit) } }
        withTimeout(5_000) { started.await() }
        withTimeout(2_000) { job.cancelAndJoin() }
        assertEquals("installed", file.readText())
        assertEquals(1, folder.root.list()!!.size)
    }
}
