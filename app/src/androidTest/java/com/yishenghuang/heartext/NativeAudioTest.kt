package com.yishenghuang.heartext

import android.content.Context
import android.media.*
import androidx.test.core.app.ApplicationProvider
import com.yishenghuang.heartext.tts.SystemTtsEngine
import com.yishenghuang.heartext.tts.streamPcm
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class NativeAudioTest {
    @Test fun pcmOutputConsumesTheFinalBufferBeforeRelease() = runBlocking {
        check(BuildConfig.APPLICATION_ID.endsWith(".validation"))
        val rate = 44_100
        val count = rate / 4
        val samples = ShortArray(count) { index ->
            (kotlin.math.sin(index * 2.0 * Math.PI * 440 / rate) * 800).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2)
            .build()
        try {
            assertEquals(AudioTrack.STATE_INITIALIZED, track.state)
            track.play()
            withTimeout(10_000) {
                streamPcm(count, {}, { offset, size -> track.write(samples, offset, size, AudioTrack.WRITE_NON_BLOCKING) },
                    { track.playbackHeadPosition.toLong() and 0xffffffffL })
            }
            assertTrue(track.playbackHeadPosition >= count)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    @Test fun installedSystemVoiceCompletesAndCanBeStopped() = runBlocking {
        check(BuildConfig.APPLICATION_ID.endsWith(".validation"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = withContext(Dispatchers.Main) { SystemTtsEngine(context) }
        try {
            // Validation builds refuse network-only voices; no synthesis request leaves the device.
            withContext(Dispatchers.Main) { engine.speak("HearText local audio check.", null) }
            withTimeout(20_000) { while (engine.isSpeaking()) delay(50) }
            withContext(Dispatchers.Main) {
                engine.speak("This sentence should stop before it finishes.", null)
                engine.pause()
                assertFalse(engine.isSpeaking())
                engine.resume()
                assertTrue(engine.isSpeaking())
                engine.stop()
            }
            delay(250)
            assertFalse(engine.isSpeaking())
        } finally {
            withContext(Dispatchers.Main) { engine.shutdown() }
        }
    }
}
