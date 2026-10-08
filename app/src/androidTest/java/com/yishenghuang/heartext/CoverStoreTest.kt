package com.yishenghuang.heartext

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.yishenghuang.heartext.data.CoverStore
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

class CoverStoreTest {
    @Test fun invalidOversizedAndCancelledReplacementsPreserveCoverAndStayInsideDirectory() = runBlocking<Unit> {
        check(BuildConfig.APPLICATION_ID.endsWith(".validation"))
        val app = ApplicationProvider.getApplicationContext<Context>()
        val root = File(app.cacheDir, "cover-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getFilesDir() = root }
        val store = CoverStore(context)
        val server = MockWebServer()
        server.start()
        try {
            val key = "../../outside"
            val path = store.generate(key, "Title", "Author")
            assertEquals(File(root, "covers").canonicalPath, File(path).canonicalFile.parent)
            val original = File(path).readBytes()
            assertNull(store.saveBytes(key, "not an image".toByteArray()))
            assertNull(store.saveBytes(key, ByteArray(16 * 1024 * 1024 + 1)))
            assertArrayEquals(original, File(path).readBytes())
            assertEquals(path, store.saveBytes(key, original))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val job = launch { store.downloadRemote(key, server.url("/cover").toString()) }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
            withTimeout(2000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
            server.enqueue(MockResponse().setBody("x".repeat(32000)).throttleBody(1024, 30, TimeUnit.SECONDS))
            val bodyJob = launch { store.downloadRemote(key, server.url("/cover").toString()) }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
            delay(100)
            withTimeout(2000) { bodyJob.cancelAndJoin() }
            assertArrayEquals(original, File(path).readBytes())
            assertEquals(1, File(root, "covers").listFiles()!!.size)
        } finally { server.shutdown(); root.deleteRecursively() }
    }
}
