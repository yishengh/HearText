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
    private var track: AudioTrack? = null

    @Volatile private var speaking = false
    @Volatile private var paused = false
    @Volatile private var stopRequested = false
    @Volatile private var volume: Float = 1f
    @Volatile private var speechRate: Float = 1f

    override suspend fun speak(text: String, voiceId: String?) {
        val id = voiceId?.trim().orEmpty()
        require(id.isNotBlank()) { "未选择离线音色" }
        offlineVoices.ensureSharedEspeakNgData()
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
        val clipped = text.trim().take(MAX_GENERATE_CHARS)
        require(clipped.isNotEmpty()) { "朗读文本为空" }
        stopRequested = false
        paused = false
        mutex.withLock {
            ensureModelLocked(pack)
            val engine = tts ?: error("离线 TTS 加载失败")
            speaking = true
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
                if (stopRequested) return@withLock
                val samples = audio.samples
                val sampleRate = audio.sampleRate.takeIf { it > 0 } ?: pack.sampleRate
                if (samples.isEmpty()) error("离线合成返回空音频")
                playSamples(samples, sampleRate)
            } catch (t: Throwable) {
                if (stopRequested) return@withLock
                throw t
            } finally {
                speaking = false
                paused = false
            }
        }
    }

    private fun ensureModelLocked(pack: OfflineVoicePack) {
        if (tts != null && loadedVoiceId == pack.voiceId) return
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
        Log.i(TAG, "Loaded offline voice ${pack.voiceId}")
    }

    private fun playSamples(samples: FloatArray, sourceRate: Int) {
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
                writeShorts(pcm16, toPcm16(pcm))
                return
            }
            val pcmFloat = createTrack(playRate, AudioFormat.ENCODING_PCM_FLOAT)
            if (pcmFloat != null) {
                if (playRate != rate) {
                    Log.i(TAG, "Resampled offline audio $rate → $playRate Hz (float)")
                }
                writeFloat(pcmFloat, pcm)
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

    private fun writeFloat(audioTrack: AudioTrack, samples: FloatArray) {
        track = audioTrack
        audioTrack.setVolume(volume)
        audioTrack.play()
        try {
            var offset = 0
            while (offset < samples.size && !stopRequested) {
                while (paused && !stopRequested) {
                    Thread.sleep(40)
                }
                if (stopRequested) break
                val chunk = minOf(2048, samples.size - offset)
                val written = audioTrack.write(samples, offset, chunk, AudioTrack.WRITE_BLOCKING)
                if (written < 0) break
                offset += chunk
            }
        } catch (t: Throwable) {
            if (!stopRequested) throw t
        } finally {
            releaseTrack(audioTrack)
        }
    }

    private fun writeShorts(audioTrack: AudioTrack, samples: ShortArray) {
        track = audioTrack
        audioTrack.setVolume(volume)
        audioTrack.play()
        try {
            var offset = 0
            while (offset < samples.size && !stopRequested) {
                while (paused && !stopRequested) {
                    Thread.sleep(40)
                }
                if (stopRequested) break
                val chunk = minOf(2048, samples.size - offset)
                val written = audioTrack.write(samples, offset, chunk, AudioTrack.WRITE_BLOCKING)
                if (written < 0) break
                offset += chunk
            }
        } catch (t: Throwable) {
            if (!stopRequested) throw t
        } finally {
            releaseTrack(audioTrack)
        }
    }

    private fun releaseTrack(audioTrack: AudioTrack) {
        runCatching {
            audioTrack.stop()
            audioTrack.release()
        }
        if (track === audioTrack) track = null
    }

    override fun stop() {
        stopRequested = true
        paused = false
        speaking = false
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
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
        stop()
        releaseModelLocked()
    }

    private fun releaseModelLocked() {
        runCatching { tts?.release() }
        tts = null
        loadedVoiceId = null
    }

    companion object {
        private const val TAG = "OfflineTtsEngine"
        /** Piper/VITS generate is heavy; keep each call short. */
        private const val MAX_GENERATE_CHARS = 120
    }
}
