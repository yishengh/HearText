package com.yishenghuang.heartext

import com.yishenghuang.heartext.tts.SystemSpeechDriver
import com.yishenghuang.heartext.tts.SystemTtsEngine
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SystemTtsEngineTest {
    @Test fun cancelledInitializationCannotSpeakLater() = runBlocking {
        val driver = Driver()
        val engine = SystemTtsEngine(driver)
        val waiting = launch(start = CoroutineStart.UNDISPATCHED) { engine.speak("Cancelled", null) }
        waiting.cancelAndJoin()
        driver.ready.complete(true)
        yield()
        assertTrue(driver.ids.isEmpty())
        assertFalse(engine.isSpeaking())
    }

    @Test fun stopDuringInitializationInvalidatesPendingRequest() = runBlocking {
        val driver = Driver()
        val engine = SystemTtsEngine(driver)
        val waiting = launch(start = CoroutineStart.UNDISPATCHED) { engine.speak("Stopped", null) }
        engine.stop()
        driver.ready.complete(true)
        waiting.join()
        assertTrue(driver.ids.isEmpty())
    }

    @Test fun pauseWhileInitializingDoesNotStartUntilResumed() = runBlocking {
        val driver = Driver()
        val engine = SystemTtsEngine(driver)
        val waiting = launch(start = CoroutineStart.UNDISPATCHED) { engine.speak("Paused", null) }
        engine.pause()
        driver.ready.complete(true)
        waiting.join()
        assertTrue(driver.ids.isEmpty())
        engine.resume()
        assertEquals(1, driver.ids.size)
    }

    @Test fun staleCallbacksCannotFinishOrRestartNewUtterance() = runBlocking {
        val driver = Driver().apply { ready.complete(true) }
        val engine = SystemTtsEngine(driver)
        engine.speak("First", null)
        val old = driver.ids.last()
        engine.speak("Second", null)
        driver.onEvent(old, SystemSpeechDriver.Event.Completed)
        driver.onEvent(old, SystemSpeechDriver.Event.Failed)
        assertTrue(engine.isSpeaking())
        val current = driver.ids.last()
        engine.pause()
        driver.onEvent(current, SystemSpeechDriver.Event.Started)
        assertFalse(engine.isSpeaking())
        engine.setVolume(0.3f)
        engine.resume()
        assertEquals(0.3f, driver.lastVolume)
        driver.onEvent(current, SystemSpeechDriver.Event.Completed)
        assertTrue(engine.isSpeaking())
        driver.onEvent(driver.ids.last(), SystemSpeechDriver.Event.Completed)
        assertFalse(engine.isSpeaking())
    }

    @Test fun immediateAndAsynchronousFailuresAreReported(): Unit = runBlocking {
        val driver = Driver().apply { ready.complete(true) }
        val engine = SystemTtsEngine(driver)
        driver.accept = false
        assertNotNull(runCatching { engine.speak("Rejected", null) }.exceptionOrNull())
        driver.accept = true
        engine.speak("Later failure", null)
        driver.onEvent(driver.ids.last(), SystemSpeechDriver.Event.Failed)
        assertThrows(IllegalStateException::class.java) { engine.isSpeaking() }
    }

    private class Driver : SystemSpeechDriver {
        override var onEvent: (String?, SystemSpeechDriver.Event) -> Unit = { _, _ -> }
        val ready = CompletableDeferred<Boolean>()
        val ids = mutableListOf<String>()
        var accept = true
        var lastVolume = 1f
        override suspend fun awaitReady() = ready.await()
        override fun speak(text: String, id: String, rate: Float, volume: Float): Boolean {
            ids += id
            lastVolume = volume
            return accept
        }
        override fun stop() {}
        override fun shutdown() {}
    }
}
