package com.yishenghuang.heartext.data

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.yishenghuang.heartext.network.ApiOfflineVoice
import com.yishenghuang.heartext.network.SessionTokenProvider
import com.yishenghuang.heartext.network.atomicDownload
import com.yishenghuang.heartext.network.consumeCancellable
import com.yishenghuang.heartext.network.HearTextApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.net.URI
import java.util.zip.ZipInputStream

data class InstalledOfflineVoice(
    val id: String,
    val name: String,
    val language: String,
    val engine: String,
    val modelType: String,
    val sampleRate: Int
)

data class OfflineVoicePack(
    val voiceId: String,
    val modelOnnx: File,
    val tokens: File?,
    val lexicon: File?,
    val dataDir: File?,
    val sampleRate: Int
) {
    val isPlayable: Boolean
        get() = tokens != null && tokens.length() > 0 &&
            dataDir != null && listOf("phontab", "phondata", "phonindex").all { File(dataDir, it).length() > 0 } &&
            hasSherpaOnnxMetadata(modelOnnx)

    companion object {
        /**
         * sherpa-onnx aborts the whole process if Piper ONNX lacks embedded
         * metadata (e.g. sample_rate). Detect before constructing OfflineTts.
         */
        fun hasSherpaOnnxMetadata(onnx: File): Boolean = OnnxMetadata.supportsVits(onnx)
    }
}

/**
 * Downloads / installs sherpa-onnx Piper packs and normalizes incomplete zips
 * (generate tokens.txt from *.onnx.json, share espeak-ng-data across voices).
 */
class OfflineVoiceRepository(
    private val app: Context,
    private val api: HearTextApi,
    private val auth: SessionTokenProvider
) {
    private val root: File
        get() = File(app.filesDir, "offline_voices").also { it.mkdirs() }

    private val sharedDir: File
        get() = File(root, "_shared").also { it.mkdirs() }

    private val sharedEspeakDir: File
        get() = File(sharedDir, "espeak-ng-data")

    private val espeakMutex = Mutex()
    private val sampleMutex = Mutex()
    private val installMutex = Mutex()
    private val sampleLock = Any()
    private val sampleGeneration = AtomicLong()
    @Volatile private var samplePlayer: MediaPlayer? = null

    suspend fun featured(language: String? = null): List<ApiOfflineVoice> = withContext(Dispatchers.IO) {
        requireSignedIn()
        api.listFeaturedOfflineVoices(language = language)
    }

    suspend fun list(language: String? = null, page: Int = 1) = withContext(Dispatchers.IO) {
        requireSignedIn()
        api.listOfflineVoices(language = language, page = page)
    }

    fun installedIds(): Set<String> = listInstalled().map { it.id }.toSet()

    fun listInstalled(): List<InstalledOfflineVoice> {
        root.listFiles()?.filter { it.name.startsWith('.') && it.name.endsWith(".backup") }?.forEach {
            val id = it.name.removePrefix(".").removeSuffix(".backup")
            if (runCatching { VoicePackageFiles.safeId(id) }.isSuccess) VoicePackageFiles.recover(File(root, id))
        }
        val dirs = root.listFiles()?.filter { it.isDirectory && !it.name.startsWith('.') && it.name != "_shared" } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            if (!isInstalled(dir.name)) return@mapNotNull null
            readMeta(dir.name) ?: InstalledOfflineVoice(
                id = dir.name,
                name = dir.name.take(8),
                language = "?",
                engine = "sherpa-onnx",
                modelType = "piper",
                sampleRate = 22050
            )
        }.sortedBy { it.name.lowercase() }
    }

    fun isInstalled(voiceId: String): Boolean = resolvePack(voiceId)?.isPlayable == true

    fun resolvePack(voiceId: String): OfflineVoicePack? {
        val dir = File(root, VoicePackageFiles.safeId(voiceId))
        VoicePackageFiles.recover(dir)
        return resolvePack(dir, voiceId, readMeta(voiceId)?.sampleRate ?: 22050)
    }

    private fun resolvePack(dir: File, voiceId: String, sampleRate: Int): OfflineVoicePack? {
        if (!dir.isDirectory) return null
        // Repair older installs that only had .onnx / .onnx.json.
        runCatching { normalizePackDir(dir) }
        val onnx = dir.walkTopDown()
            .firstOrNull { it.isFile && it.name.endsWith(".onnx") && !it.name.endsWith(".onnx.json") }
            ?: return null
        val tokens = dir.walkTopDown().firstOrNull { it.isFile && it.name.equals("tokens.txt", ignoreCase = true) }
        val lexicon = dir.walkTopDown().firstOrNull {
            it.isFile && (
                it.name.equals("lexicon.txt", ignoreCase = true) ||
                    it.name.equals("lexicon.lst", ignoreCase = true)
                )
        }
        val localEspeak = dir.walkTopDown()
            .firstOrNull { it.isDirectory && it.name.equals("espeak-ng-data", ignoreCase = true) }
        val dataDir = when {
            localEspeak != null && isEspeakDataDir(localEspeak) -> localEspeak
            isEspeakDataDir(sharedEspeakDir) -> sharedEspeakDir
            else -> localEspeak
        }
        return OfflineVoicePack(
            voiceId = voiceId,
            modelOnnx = onnx,
            tokens = tokens,
            lexicon = lexicon,
            dataDir = dataDir,
            sampleRate = sampleRate
        )
    }

    fun modelFiles(voiceId: String): Pair<File, File>? {
        val pack = resolvePack(voiceId) ?: return null
        val json = File(pack.modelOnnx.absolutePath + ".json").takeIf { it.exists() }
            ?: File(root, voiceId).walkTopDown().firstOrNull { it.isFile && it.name.endsWith(".onnx.json") }
            ?: return null
        return pack.modelOnnx to json
    }

    suspend fun downloadAndInstall(
        voice: ApiOfflineVoice,
        onProgress: ((bytesRead: Long, contentLength: Long) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        requireSignedIn()
        val id = VoicePackageFiles.safeId(voice.id)
        installMutex.withLock {
            val staging = File(root, ".$id-${UUID.randomUUID()}.staging").apply { mkdirs() }
            val zip = File(root, ".$id-${UUID.randomUUID()}.zip")
            val coroutine = currentCoroutineContext()
            try {
                api.downloadOfflineVoice(id, zip) { read, total ->
                    require(read <= 1024L * 1024 * 1024) { "Voice archive is too large" }
                    onProgress?.invoke(read, total)
                }
                VoicePackageFiles.verifyChecksum(zip, voice.checksumSha256) { coroutine.ensureActive() }
                VoicePackageFiles.unzip(zip, staging) { coroutine.ensureActive() }
                flattenSingleRootIfNeeded(staging)
                normalizePackDir(staging)
                var pack = resolvePack(staging, id, voice.sampleRate)
                require(pack != null && OfflineVoicePack.hasSherpaOnnxMetadata(pack.modelOnnx) && (pack.tokens?.length() ?: 0L) > 0L) {
                    "Invalid or unsupported offline voice package"
                }
                if (!isEspeakDataDir(pack.dataDir)) {
                    ensureSharedEspeakNgData()
                    pack = resolvePack(staging, id, voice.sampleRate)
                }
                require(pack?.isPlayable == true) { "Incomplete offline voice package" }
                writeMeta(voice, staging)
                coroutine.ensureActive()
                val destination = File(root, id)
                VoicePackageFiles.commit(staging, destination)
                destination
            } finally {
                staging.deleteRecursively()
                zip.delete()
            }
        }
    }
    /**
     * Ensures shared espeak-ng-data exists (required by all Piper voices).
     * Safe to call before speaking if an older install lacked it.
     */
    suspend fun ensureSharedEspeakNgData() = withContext(Dispatchers.IO) {
        espeakMutex.withLock {
            if (isEspeakDataDir(sharedEspeakDir)) return@withLock
            downloadAndExtractEspeakNgData()
        }
    }

    /**
     * Downloads (if needed) and plays the catalog sample for [voiceId].
     * Only one sample plays at a time; call [stopSample] to halt.
     */
    suspend fun playSample(
        voiceId: String,
        onFinished: (() -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        requireSignedIn()
        VoicePackageFiles.safeId(voiceId)
        val request = sampleGeneration.incrementAndGet()
        synchronized(sampleLock) { stopSampleLocked() }
        sampleMutex.withLock {
            val coroutine = currentCoroutineContext()
            fun checkCurrent() {
                coroutine.ensureActive()
                if (request != sampleGeneration.get()) throw kotlinx.coroutines.CancellationException("Sample stopped")
            }
            checkCurrent()
            val sample = File(root, "${voiceId}_sample.mp3")
            if (!sample.exists() || sample.length() == 0L) api.downloadOfflineVoiceSample(voiceId, sample)
            checkCurrent()
            val player = MediaPlayer()
            fun finish() {
                val current = synchronized(sampleLock) {
                    if (samplePlayer === player && sampleGeneration.get() == request) {
                        stopSampleLocked()
                        true
                    } else false
                }
                if (current) onFinished?.invoke()
            }
            try {
                player.setDataSource(sample.absolutePath)
                player.setOnCompletionListener { finish() }
                player.setOnErrorListener { _, _, _ -> finish(); true }
                player.prepare()
                synchronized(sampleLock) {
                    checkCurrent()
                    samplePlayer = player
                    player.start()
                }
            } catch (failure: Throwable) {
                synchronized(sampleLock) {
                    if (samplePlayer === player) samplePlayer = null
                    runCatching { player.release() }
                }
                throw failure
            }
        }
    }

    internal fun isSamplePlaying(): Boolean = synchronized(sampleLock) {
        runCatching { samplePlayer?.isPlaying == true }.getOrDefault(false)
    }

    fun stopSample() {
        sampleGeneration.incrementAndGet()
        synchronized(sampleLock) { stopSampleLocked() }
    }

    private fun stopSampleLocked() {
        val player = samplePlayer ?: return
        samplePlayer = null
        runCatching { player.stop() }
        runCatching { player.release() }
    }
    /** If zip wrapped everything in one folder, lift contents up one level. */
    private fun flattenSingleRootIfNeeded(outDir: File) {
        val children = outDir.listFiles()?.filter { it.name != "heartext_meta.json" } ?: return
        if (children.size != 1 || !children[0].isDirectory) return
        val only = children[0]
        // Don't flatten if the only dir is already a model-ish root with onnx inside nested deeper only.
        val nested = only.listFiles() ?: return
        nested.forEach { child ->
            val dest = File(outDir, child.name)
            if (!dest.exists()) child.renameTo(dest)
        }
        only.deleteRecursively()
    }

    private fun normalizePackDir(dir: File) {
        generateTokensIfNeeded(dir)
    }

    private fun generateTokensIfNeeded(dir: File) {
        val existing = dir.walkTopDown()
            .firstOrNull { it.isFile && it.name.equals("tokens.txt", ignoreCase = true) }
        if (existing != null && existing.length() > 0L) return
        val json = dir.walkTopDown()
            .firstOrNull { it.isFile && it.name.endsWith(".onnx.json", ignoreCase = true) }
            ?: dir.walkTopDown().firstOrNull {
                it.isFile && it.name.endsWith(".json", ignoreCase = true) &&
                    !it.name.equals("heartext_meta.json", ignoreCase = true)
            }
            ?: return
        val onnx = dir.walkTopDown()
            .firstOrNull { it.isFile && it.name.endsWith(".onnx") && !it.name.endsWith(".onnx.json") }
        val tokensOut = when {
            onnx != null -> File(onnx.parentFile, "tokens.txt")
            else -> File(dir, "tokens.txt")
        }
        runCatching {
            writeTokensFromPiperJson(json, tokensOut)
            Log.i(TAG, "Generated tokens.txt for ${dir.name} from ${json.name}")
        }.onFailure {
            Log.w(TAG, "Failed to generate voice tokens (${it.javaClass.simpleName})")
        }
    }

    private fun writeTokensFromPiperJson(jsonFile: File, tokensOut: File) {
        val rootJson = JSONObject(jsonFile.readText())
        val idMap = rootJson.optJSONObject("phoneme_id_map")
            ?: error("onnx.json missing phoneme_id_map")
        tokensOut.bufferedWriter(Charsets.UTF_8).use { writer ->
            val keys = idMap.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key == "\n") continue
                val raw = idMap.get(key)
                val id = when (raw) {
                    is Int -> raw
                    is JSONArray -> raw.optInt(0)
                    is Number -> raw.toInt()
                    else -> error("Unexpected phoneme id for $key")
                }
                writer.append(key).append(' ').append(id.toString()).append('\n')
            }
        }
    }

    private suspend fun downloadAndExtractEspeakNgData() {
        val staging = File(sharedDir, ".extract-${UUID.randomUUID()}").apply { mkdirs() }
        val archive = File(sharedDir, ".espeak-${UUID.randomUUID()}.tar.bz2")
        val coroutine = currentCoroutineContext()
        try {
            val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS).build()
            client.newCall(Request.Builder().url(ESPEAK_DATA_URL).build()).consumeCancellable { response ->
                check(response.isSuccessful) { "Shared voice data download failed" }
                val body = response.body ?: error("Empty shared voice response")
                atomicDownload(body.byteStream(), archive, body.contentLength(), { coroutine.ensureActive() }) { read, _ ->
                    require(read <= 100L * 1024 * 1024) { "Shared voice archive is too large" }
                }
            }
            extractTarBz2(archive, staging) { coroutine.ensureActive() }
            val extracted = File(staging, "espeak-ng-data")
            require(isEspeakDataDir(extracted)) { "Incomplete shared voice data" }
            coroutine.ensureActive()
            VoicePackageFiles.commit(extracted, sharedEspeakDir)
        } finally {
            archive.delete()
            staging.deleteRecursively()
        }
    }

    private fun extractTarBz2(archive: File, destDir: File, checkActive: () -> Unit) {
        var total = 0L
        var count = 0
        BufferedInputStream(archive.inputStream()).use { fileIn ->
            BZip2CompressorInputStream(fileIn).use { bzIn ->
                TarArchiveInputStream(bzIn).use { tar ->
                    while (true) {
                        checkActive()
                        val entry = tar.nextEntry ?: break
                        require(++count <= 20_000 && !entry.isSymbolicLink && !entry.isLink) { "Unsupported shared voice archive" }
                        val out = VoicePackageFiles.entryFile(destDir, entry.name)
                        if (entry.isDirectory) out.mkdirs() else {
                            require(entry.isFile) { "Unsupported archive entry" }
                            out.parentFile?.mkdirs()
                            out.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    checkActive()
                                    val size = tar.read(buffer)
                                    if (size < 0) break
                                    total += size
                                    require(total <= 256L * 1024 * 1024) { "Shared voice data is too large" }
                                    output.write(buffer, 0, size)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isEspeakDataDir(dir: File?): Boolean = dir != null &&
        listOf("phontab", "phondata", "phonindex").all { File(dir, it).length() > 0L }
    private fun writeMeta(voice: ApiOfflineVoice, directory: File) {
        val meta = JSONObject()
            .put("id", voice.id)
            .put("name", voice.name)
            .put("language", voice.language)
            .put("engine", voice.engine)
            .put("model_type", voice.modelType)
            .put("sample_rate", voice.sampleRate)
        directory.resolve("heartext_meta.json").writeText(meta.toString())
    }

    private fun readMeta(voiceId: String): InstalledOfflineVoice? {
        val file = File(root, voiceId).resolve("heartext_meta.json")
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            InstalledOfflineVoice(
                id = json.optString("id", voiceId),
                name = json.optString("name", voiceId),
                language = json.optString("language", "?"),
                engine = json.optString("engine", "sherpa-onnx"),
                modelType = json.optString("model_type", "piper"),
                sampleRate = json.optInt("sample_rate", 22050)
            )
        }.getOrNull()
    }

    private fun requireSignedIn() {
        if (!auth.isSignedIn || !api.isConfigured) error("Sign in required")
    }

    companion object {
        private const val TAG = "OfflineVoiceRepo"
        private const val ESPEAK_DATA_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/espeak-ng-data.tar.bz2"
    }
}
