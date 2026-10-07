package com.yishenghuang.heartext.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID

/** Platform boundary also used by deterministic lifecycle tests. */
internal interface SystemSpeechDriver {
    enum class Event { Started, Completed, Failed }
    var onEvent: (String?, Event) -> Unit
    suspend fun awaitReady(): Boolean
    fun speak(text: String, id: String, rate: Float, volume: Float): Boolean
    fun stop()
    fun shutdown()
}

class SystemTtsEngine internal constructor(private val driver: SystemSpeechDriver) : TtsEngine {
    constructor(context: Context) : this(AndroidSystemSpeechDriver(context))
    override val name = "System"
    private val lock = Any()
    private var generation = 0L
    private var activeId: String? = null
    private var pendingText: String? = null
    private var speaking = false
    private var loading = false
    private var paused = false
    private var closed = false
    private var failure = false
    private var speechRate = 1f
    private var volume = 1f

    init {
        driver.onEvent = { id, event ->
            synchronized(lock) {
                if (id != null && id == activeId) {
                    when (event) {
                        SystemSpeechDriver.Event.Started -> speaking = !paused
                        SystemSpeechDriver.Event.Completed -> speaking = false
                        SystemSpeechDriver.Event.Failed -> { speaking = false; failure = true }
                    }
                }
            }
        }
    }

    override suspend fun speak(text: String, voiceId: String?) {
        val request = synchronized(lock) {
            check(!closed) { "System TTS is closed" }
            activeId = null
            pendingText = text
            paused = false
            speaking = true
            loading = true
            failure = false
            ++generation
        }
        try {
            check(withTimeoutOrNull(5_000) { driver.awaitReady() } == true) { "System TTS is unavailable" }
            currentCoroutineContext().ensureActive()
            synchronized(lock) {
                if (request != generation || closed) return
                loading = false
                if (!paused) enqueue(text)
            }
        } catch (error: Exception) {
            synchronized(lock) { if (request == generation) stop() }
            throw error
        }
    }

    private fun enqueue(text: String) {
        val id = UUID.randomUUID().toString()
        activeId = id
        paused = false
        speaking = true
        failure = false
        if (!driver.speak(text, id, speechRate, volume)) {
            activeId = null
            speaking = false
            failure = true
            error("System TTS rejected playback")
        }
    }

    override fun setSpeechRate(rate: Float) = synchronized(lock) {
        speechRate = rate.coerceIn(0.5f, 2.5f)
    }

    override fun setVolume(volume: Float) = synchronized(lock) {
        this.volume = volume.coerceIn(0f, 1f)
        // Applied to the next utterance; speech focus loss is handled by pausing.
    }

    override fun stop() = synchronized(lock) {
        generation++
        activeId = null // Invalidate callbacks before calling into the platform.
        speaking = false
        loading = false
        paused = false
        failure = false
        pendingText = null
        driver.stop()
    }

    override fun pause() = synchronized(lock) {
        if (speaking || loading) {
            activeId = null
            paused = true
            speaking = false
            driver.stop()
        }
    }

    override fun resume() = synchronized(lock) {
        if (paused) {
            paused = false
            if (loading) speaking = true else pendingText?.let(::enqueue)
        }
        Unit
    }

    override fun isSpeaking(): Boolean = synchronized(lock) {
        check(!failure) { "System TTS playback failed" }
        speaking
    }

    override fun shutdown() = synchronized(lock) {
        stop()
        closed = true
        driver.shutdown()
    }
}

private class AndroidSystemSpeechDriver(context: Context) : SystemSpeechDriver {
    private val initialized = CompletableDeferred<Boolean>()
    @Volatile override var onEvent: (String?, SystemSpeechDriver.Event) -> Unit = { _, _ -> }
    private val tts = TextToSpeech(context.applicationContext) { status ->
        initialized.complete(status == TextToSpeech.SUCCESS)
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = onEvent(id, SystemSpeechDriver.Event.Started)
            override fun onDone(id: String?) = onEvent(id, SystemSpeechDriver.Event.Completed)
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) = onEvent(id, SystemSpeechDriver.Event.Failed)
            override fun onError(id: String?, errorCode: Int) = onEvent(id, SystemSpeechDriver.Event.Failed)
        })
    }

    override suspend fun awaitReady() = initialized.await()

    override fun speak(text: String, id: String, rate: Float, volume: Float): Boolean {
        val languageResult = tts.setLanguage(Locale.getDefault())
        if (languageResult < TextToSpeech.LANG_AVAILABLE) return false
        val localVoice = tts.voices.orEmpty().filter {
            !it.isNetworkConnectionRequired && it.locale.language == Locale.getDefault().language
        }.sortedByDescending { it.locale.country == Locale.getDefault().country }.firstOrNull()
        if (localVoice != null) tts.voice = localVoice
        else if (com.yishenghuang.heartext.BuildConfig.APPLICATION_ID.endsWith(".validation")) return false
        tts.setSpeechRate(rate)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, id) == TextToSpeech.SUCCESS
    }

    override fun stop() { tts.stop() }
    override fun shutdown() { tts.shutdown() }
}
