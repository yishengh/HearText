package com.yishenghuang.heartext

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.yishenghuang.heartext.data.*
import com.yishenghuang.heartext.network.*
import com.yishenghuang.heartext.tts.OfflineTtsEngine
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.TimeUnit

class OfflineVoiceRepositoryTest {
    private lateinit var root: File
    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private lateinit var repository: OfflineVoiceRepository
    private val auth = object : SessionTokenProvider {
        override val sessionKey = "fixture-session"
        override val isSignedIn = true
        override suspend fun getToken(forceRefresh: Boolean) = "local-test-token"
    }

    @Before fun setUp() {
        check(BuildConfig.APPLICATION_ID.endsWith(".validation"))
        val app = ApplicationProvider.getApplicationContext<Context>()
        root = File(app.cacheDir, "voice-test-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(app) { override fun getFilesDir() = root }
        server = MockWebServer().apply { start() }
        repository = OfflineVoiceRepository(context, HearTextApi(auth, endpoint = server.url("/").toString()), auth)
    }

    @After fun tearDown() {
        repository.stopSample()
        server.shutdown()
        root.deleteRecursively()
    }

    @Test fun corruptReplacementPreservesPreviousInstallation() = runBlocking {
        val old = File(root, "offline_voices/voice").apply { mkdirs(); resolve("previous").writeText("keep") }
        server.enqueue(MockResponse().setBody("not the expected zip"))
        val failure = runCatching { repository.downloadAndInstall(voice("voice", "0".repeat(64))) }.exceptionOrNull()
        assertNotNull(failure)
        assertEquals("keep", old.resolve("previous").readText())
        assertTrue(old.parentFile!!.listFiles().orEmpty().none { it.name.endsWith(".staging") || it.name.endsWith(".zip") })
    }

    @Test fun cancellationPreservesOldVoiceAndRemovesStaging() = runBlocking {
        val old = File(root, "offline_voices/voice").apply { mkdirs(); resolve("previous").writeText("keep") }
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(500_000))).throttleBody(1024, 1, TimeUnit.SECONDS))
        val job = launch { repository.downloadAndInstall(voice("voice")) }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
        withTimeout(2000) { job.cancelAndJoin() }
        assertEquals("keep", old.resolve("previous").readText())
        assertTrue(old.parentFile!!.listFiles().orEmpty().none { it.name.endsWith(".staging") || it.name.endsWith(".zip") || it.name.endsWith(".part") })
    }

    @Test fun stoppedSampleCannotStartWhenDownloadFinishes() = runBlocking {
        server.enqueue(MockResponse().setBody(Buffer().write(wav())).setBodyDelay(300, TimeUnit.MILLISECONDS))
        val job = launch { repository.playSample("voice") }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
        repository.stopSample()
        withTimeout(5000) { job.join() }
        assertTrue(job.isCancelled)
        assertFalse(repository.isSamplePlaying())
    }

    @Test fun officialVoiceInstallsAndSynthesizesLocally() = runBlocking {
        val endpoint = InstrumentationRegistry.getArguments().getString("offlineFixtureEndpoint")
        Assume.assumeTrue("Run verify-local.ps1 -Connected -OfflineFixture for the official model test", endpoint != null)
        val uri = URI(endpoint!!)
        require(uri.scheme == "http" && uri.host == "10.0.2.2") { "Only a local emulator fixture server is allowed" }
        val checksum = withContext(Dispatchers.IO) { URI("${endpoint.trimEnd('/')}/checksum.txt").toURL().readText().trim() }
        val nativeRepository = OfflineVoiceRepository(context, HearTextApi(auth, endpoint = endpoint), auth)
        nativeRepository.downloadAndInstall(voice("local-amy", checksum))
        assertTrue(nativeRepository.isInstalled("local-amy"))
        val engine = OfflineTtsEngine(context, nativeRepository)
        try {
            withTimeout(60_000) { engine.speak("Local offline speech test.", "local-amy") }
            assertFalse(engine.isSpeaking())
            val speaking = launch { engine.speak("This second sentence is used to check pause and stop during offline synthesis.", "local-amy") }
            withTimeout(5000) { while (!engine.isSpeaking()) delay(10) }
            engine.pause()
            assertFalse(engine.isSpeaking())
            delay(150)
            engine.resume()
            assertTrue(engine.isSpeaking())
            engine.stop()
            withTimeout(15_000) { speaking.join() }
            assertFalse(engine.isSpeaking())
        } finally { engine.shutdown() }
    }

    private fun voice(id: String, checksum: String? = null) = ApiOfflineVoice(
        id, id, "Local fixture", "sherpa-onnx", "piper", "en", null, 16000,
        "fixture", "see MODEL_CARD", null, 0, 0, false, true, null, checksum
    )

    private fun wav(): ByteArray {
        val bytes = 32_000
        return ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + bytes); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(16000); putInt(32000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(bytes); put(ByteArray(bytes))
        }.array()
    }
}
