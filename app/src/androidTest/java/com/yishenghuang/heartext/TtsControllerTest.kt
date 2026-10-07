package com.yishenghuang.heartext

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.yishenghuang.heartext.tts.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class TtsControllerTest {
    @Test fun fallbackPauseResumeAndCancellationControlTheActualEngine() = runBlocking {
        check(BuildConfig.APPLICATION_ID.endsWith(".validation"))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val system = FakeEngine()
        val offline = FakeEngine(fail = true)
        val controller = TtsController(app, scope, system, offline)
        try {
            withContext(Dispatchers.Main) {
                controller.configure(TtsMode.OFFLINE, "test-local-voice")
                controller.play("First sentence. Second sentence.")
            }
            withTimeout(5_000) { while (system.calls.get() == 0) delay(20) }
            withContext(Dispatchers.Main) {
                controller.pause()
                assertEquals(TtsPlaybackState.Paused, controller.state.value)
                assertEquals(1, system.pauses)
                assertEquals(0, offline.pauses)
                controller.resume()
                assertEquals(1, system.resumes)
                system.speaking = false
            }
            withTimeout(5_000) { while (system.calls.get() < 2) delay(20) }
            assertEquals("Failed offline engine must not be retried for every sentence", 1, offline.calls.get())
            withContext(Dispatchers.Main) { controller.stop() }
            delay(250)
            assertFalse(system.speaking)
            assertEquals(2, system.calls.get())
            assertEquals(TtsPlaybackState.Idle, controller.state.value)
        } finally {
            withContext(Dispatchers.Main) { controller.shutdown() }
            scope.cancel()
        }
    }

    private class FakeEngine(val fail: Boolean = false) : TtsEngine {
        override val name = "Test"
        val calls = AtomicInteger()
        @Volatile var speaking = false
        var pauses = 0
        var resumes = 0
        override suspend fun speak(text: String, voiceId: String?) {
            calls.incrementAndGet()
            if (fail) error("Local simulated engine failure")
            speaking = true
        }
        override fun stop() { speaking = false }
        override fun pause() { pauses++; speaking = false }
        override fun resume() { resumes++; speaking = true }
        override fun isSpeaking() = speaking
        override fun shutdown() { stop() }
    }
}
