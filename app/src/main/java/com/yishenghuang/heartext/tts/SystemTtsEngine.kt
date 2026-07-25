package com.yishenghuang.heartext.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SystemTtsEngine(context: Context) : TtsEngine {
    override val name: String = "System"

    private var tts: TextToSpeech? = null
    private var ready = false
    private var speaking = false
    @Volatile private var paused = false
    private var pendingText: String? = null
    @Volatile private var speechRate: Float = 1f

    private val initLock = Any()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            synchronized(initLock) {
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    tts?.language = Locale.getDefault()
                }
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                speaking = true
                paused = false
            }

            override fun onDone(utteranceId: String?) {
                speaking = false
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                speaking = false
            }
        })
    }

    private suspend fun awaitReady() {
        if (ready) return
        suspendCancellableCoroutine { cont ->
            val start = System.currentTimeMillis()
            fun poll() {
                if (ready) {
                    cont.resume(Unit)
                } else if (System.currentTimeMillis() - start > 5000) {
                    cont.resumeWithException(IllegalStateException("System TTS failed to initialize"))
                } else {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ poll() }, 50)
                }
            }
            poll()
        }
    }

    override suspend fun speak(text: String, voiceId: String?) {
        awaitReady()
        pendingText = text
        paused = false
        val engine = tts ?: error("TTS unavailable")
        engine.setSpeechRate(speechRate.coerceIn(0.5f, 2.5f))
        val id = UUID.randomUUID().toString()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        speaking = true
    }

    override fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 2.5f)
        tts?.setSpeechRate(speechRate)
    }

    override fun stop() {
        tts?.stop()
        speaking = false
        paused = false
        pendingText = null
    }

    override fun pause() {
        if (speaking) {
            tts?.stop()
            paused = true
            speaking = false
        }
    }

    override fun resume() {
        val text = pendingText ?: return
        if (paused) {
            tts?.setSpeechRate(speechRate.coerceIn(0.5f, 2.5f))
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
            speaking = true
            paused = false
        }
    }

    override fun isSpeaking(): Boolean = speaking

    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
