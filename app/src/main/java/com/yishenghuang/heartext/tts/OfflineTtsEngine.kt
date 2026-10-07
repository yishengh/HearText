package com.yishenghuang.heartext.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.yishenghuang.heartext.data.OfflineVoicePack
import com.yishenghuang.heartext.data.OfflineVoiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * On-device neural TTS via sherpa-onnx (Piper / VITS packs from /v1/offline-voices).
 *
 * Raw Piper ONNX without sherpa metadata will abort the process — we preflight
 * [OfflineVoicePack.hasSherpaOnnxMetadata] and throw a Java exception instead.
 */
class OfflineTtsEngine(
    context: Context,
    private val offlineVoices: OfflineVoiceRepository
) : TtsEngine {
    override val name: String = "Offline"

    private val mutex = Mutex()
    private var tts: OfflineTts? = null
    private var loadedVoiceId: String? = null
    private var loadedModelStamp: Pair<Long, Long>? = null
    @Volatile private var track: AudioTrack? = null
    private val generation = AtomicLong()
    @Volatile private var closed = false

    @Volatile private var speaking = false
    @Volatile private var paused = false
    @Volatile private var volume: Float = 1f
    @Volatile private var speechRate: Float = 1f

    override suspend fun speak(text: String, voiceId: String?) {
        check(!closed) { "Offline TTS is closed" }
        val request = generation.incrementAndGet()
        paused = false
        speaking = true
        try {
            withContext(Dispatchers.Default) {
                mutex.withLock {
                    awaitPlayable(request)
                    synthesizeAndPlay(text, voiceId, request)
                }
            }
        } finally {
            if (generation.get() == request) {
                speaking = false
                paused = false
            }
        }
    }

    private suspend fun synthesizeAndPlay(text: String, voiceId: String?, request: Long) {
        val id = voiceId?.trim().orEmpty()
        require(id.isNotBlank()) { "未选择离线音色" }
        // Playback is offline: installation is responsible for shared voice assets.
        val pack = offlineVoices.resolvePack(id)
            ?: error("离线音色未安装，请先在「我的」下载")
        if (!OfflineVoicePack.hasSherpaOnnxMetadata(pack.modelOnnx)) {
            error(
                "该离线音色是原始 Piper 模型，缺少 sherpa 元数据（sample_rate），" +
                    "加载会闪退。请让管理员用 sherpa-onnx 转换后再上传。"
            )
        }
        if (!pack.isPlayable) {
            error("离线语音包不完整（需要 tokens.txt + espeak-ng-data），请重新下载")
        }
        // Keep offline synthesis chunks short — long generate() is heavy / crash-prone.
        val clipped = text.trim()
        require(clipped.isNotEmpty()) { "朗读文本为空" }
        require(clipped.length <= MAX_GENERATE_CHARS) { "Offline TTS chunk exceeds safe synthesis limit" }
        awaitPlayable(request)
        ensureModelLocked(pack)
        val engine = tts ?: error("离线 TTS 加载失败")
        try {
            val audio = withContext(Dispatchers.Default) {
                try {
                    engine.generate(
                        text = clipped,
                        sid = 0,
                        speed = speechRate.coerceIn(0.5f, 2.5f)
                    )
                } catch (t: Throwable) {
                    releaseModelLocked()
                    throw IllegalStateException("离线合成失败: ${t.message}", t)
                }
            }
            awaitPlayable(request)
            val samples = audio.samples
            val sampleRate = audio.sampleRate.takeIf { it > 0 } ?: pack.sampleRate
            if (samples.isEmpty()) error("离线合成返回空音频")
            playSamples(samples, sampleRate, request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            ensureCurrent(request)
            throw failure
        }
    }

    private suspend fun ensureCurrent(request: Long) {
        currentCoroutineContext().ensureActive()
        if (generation.get() != request || closed) throw CancellationException("Playback replaced or stopped")
    }

    private suspend fun awaitPlayable(request: Long) {
        ensureCurrent(request)
        while (paused) {
            delay(40)
            ensureCurrent(request)
        }
    }

    private fun ensureModelLocked(pack: OfflineVoicePack) {
        val stamp = pack.modelOnnx.length() to pack.modelOnnx.lastModified()
        if (tts != null && loadedVoiceId == pack.voiceId && loadedModelStamp == stamp) return
        releaseModelLocked()
        val tokensPath = pack.tokens?.absolutePath.orEmpty()
        val dataDirPath = pack.dataDir?.absolutePath.orEmpty()
        if (tokensPath.isBlank() || dataDirPath.isBlank()) {
            error("离线语音包不完整（需要 tokens.txt + espeak-ng-data），请重新下载")
        }
        if (!OfflineVoicePack.hasSherpaOnnxMetadata(pack.modelOnnx)) {
            error(
                "该离线音色缺少 sherpa ONNX 元数据（sample_rate），无法安全加载"
            )
        }
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = pack.modelOnnx.absolutePath,
                    lexicon = pack.lexicon?.absolutePath.orEmpty(),
                    tokens = tokensPath,
                    dataDir = dataDirPath,
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            )
        )
        tts = try {
            OfflineTts(config = config)
        } catch (t: Throwable) {
            releaseModelLocked()
            throw IllegalStateException(
                "离线音色加载失败（需 sherpa 转换后的 Piper 包）: ${t.message}",
                t
            )
        }
        loadedVoiceId = pack.voiceId
        loadedModelStamp = stamp
        Log.i(TAG, "Loaded offline voice ${pack.voiceId}")
    }

    private suspend fun playSamples(samples: FloatArray, sourceRate: Int, request: Long) {
        val rate = sourceRate.takeIf { it > 0 } ?: 22050
        val candidates = linkedSetOf(
            AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC),
            44_100,
            48_000,
            rate,
            22_050,
            16_000
        ).filter { it in 4_000..192_000 }

        var lastError: String? = null
        for (playRate in candidates) {
            val pcm = if (playRate == rate) samples else resampleLinear(samples, rate, playRate)
            // PCM_16BIT has the widest device support; float is optional.
            val pcm16 = createTrack(playRate, AudioFormat.ENCODING_PCM_16BIT)
            if (pcm16 != null) {
                if (playRate != rate) {
                    Log.i(TAG, "Resampled offline audio $rate → $playRate Hz (pcm16)")
                }
                val shorts = toPcm16(pcm)
                writeSamples(pcm16, shorts.size, request) { offset, count ->
                    pcm16.write(shorts, offset, count, AudioTrack.WRITE_NON_BLOCKING)
                }
                return
            }
            val pcmFloat = createTrack(playRate, AudioFormat.ENCODING_PCM_FLOAT)
            if (pcmFloat != null) {
                if (playRate != rate) {
                    Log.i(TAG, "Resampled offline audio $rate → $playRate Hz (float)")
                }
                writeSamples(pcmFloat, pcm.size, request) { offset, count ->
                    pcmFloat.write(pcm, offset, count, AudioTrack.WRITE_NON_BLOCKING)
                }
                return
            }
            lastError = "rate=$playRate"
        }
        error("无法创建 AudioTrack（sourceRate=$rate${lastError?.let { ", tried $it" } ?: ""}）")
    }

    private fun toPcm16(samples: FloatArray): ShortArray =
        ShortArray(samples.size) { i ->
            (samples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }

    /** Simple linear resampler — enough for speech when device rejects Piper's 22050 Hz. */
    private fun resampleLinear(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (input.isEmpty() || fromRate <= 0 || toRate <= 0 || fromRate == toRate) return input
        val outLen = ((input.size.toLong() * toRate) / fromRate).toInt().coerceAtLeast(1)
        val out = FloatArray(outLen)
        val step = fromRate.toDouble() / toRate
        var src = 0.0
        for (i in 0 until outLen) {
            val i0 = src.toInt().coerceIn(0, input.lastIndex)
            val i1 = (i0 + 1).coerceAtMost(input.lastIndex)
            val frac = (src - i0).toFloat()
            out[i] = input[i0] * (1f - frac) + input[i1] * frac
            src += step
        }
        return out
    }

    private fun createTrack(sampleRate: Int, encoding: Int): AudioTrack? {
        val channel = AudioFormat.CHANNEL_OUT_MONO
        val min = AudioTrack.getMinBufferSize(sampleRate, channel, encoding)
        if (min <= 0) return null
        // Some devices need a larger stream buffer than the reported minimum.
        val bufLength = (min * 2).coerceAtLeast(sampleRate / 2)
        val attr = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(encoding)
            .setChannelMask(channel)
            .setSampleRate(sampleRate)
            .build()
        return runCatching {
            AudioTrack(
                attr,
                format,
                bufLength,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            ).also { track ->
                if (track.state != AudioTrack.STATE_INITIALIZED) {
                    track.release()
                    error("AudioTrack not initialized")
                }
            }
        }.getOrNull()
    }

    private suspend fun writeSamples(
        audioTrack: AudioTrack,
        sampleCount: Int,
        request: Long,
        write: (Int, Int) -> Int
    ) {
        try {
            awaitPlayable(request)
            track = audioTrack
            audioTrack.setVolume(volume)
            awaitPlayable(request)
            audioTrack.play()
            streamPcm(
                sampleCount,
                awaitPlayable = { awaitPlayable(request) },
                write = write,
                playedFrames = { audioTrack.playbackHeadPosition.toLong() and 0xffffffffL }
            )
        } finally {
            releaseTrack(audioTrack)
        }
    }
    private fun releaseTrack(audioTrack: AudioTrack) {
        runCatching { audioTrack.stop() }
        runCatching { audioTrack.release() }
        if (track === audioTrack) track = null
    }

    override fun stop() {
        generation.incrementAndGet()
        paused = false
        speaking = false
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        // The writing coroutine owns release, avoiding a use-after-release race.
    }

    override fun pause() {
        if (speaking) {
            paused = true
            speaking = false
            runCatching { track?.pause() }
        }
    }

    override fun resume() {
        if (paused) {
            paused = false
            speaking = true
            runCatching { track?.play() }
        }
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        runCatching { track?.setVolume(this.volume) }
    }

    override fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 2.5f)
    }

    override fun isSpeaking(): Boolean = speaking && !paused

    override fun shutdown() {
        closed = true
        stop()
        // Native synthesis cannot be interrupted safely. Release only after it exits.
        CoroutineScope(Dispatchers.Default).launch {
            mutex.withLock { releaseModelLocked() }
        }
    }

    private fun releaseModelLocked() {
        runCatching { tts?.release() }
        tts = null
        loadedVoiceId = null
        loadedModelStamp = null
    }

    companion object {
        private const val TAG = "OfflineTtsEngine"
        /** Piper/VITS generate is heavy; keep each call short. */
        private const val MAX_GENERATE_CHARS = 120
    }
}
