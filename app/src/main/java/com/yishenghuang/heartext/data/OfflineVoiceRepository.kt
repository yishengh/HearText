package com.yishenghuang.heartext.data

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.yishenghuang.heartext.network.ApiOfflineVoice
import com.yishenghuang.heartext.network.AuthTokenProvider
import com.yishenghuang.heartext.network.HearTextApi
import kotlinx.coroutines.Dispatchers
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
        get() = tokens != null && tokens.exists() &&
            dataDir != null && dataDir.isDirectory &&
            hasSherpaOnnxMetadata(modelOnnx)

    companion object {
        /**
         * sherpa-onnx aborts the whole process if Piper ONNX lacks embedded
         * metadata (e.g. sample_rate). Detect before constructing OfflineTts.
         */
        fun hasSherpaOnnxMetadata(onnx: File): Boolean {
            if (!onnx.isFile || onnx.length() < 64L) return false
            return runCatching {
                onnx.inputStream().buffered().use { input ->
                    val needle = "sample_rate".toByteArray(Charsets.US_ASCII)
                    val window = ByteArray(64 * 1024)
                    var carry = ByteArray(0)
                    while (true) {
                        val n = input.read(window)
                        if (n <= 0) break
                        val chunk = if (carry.isEmpty()) {
                            window.copyOf(n)
                        } else {
                            carry + window.copyOf(n)
                        }
                        if (indexOf(chunk, needle) >= 0) return@use true
                        carry = if (chunk.size >= needle.size - 1) {
                            chunk.copyOfRange(chunk.size - (needle.size - 1), chunk.size)
                        } else {
                            chunk
                        }
                    }
                    false
                }
            }.getOrDefault(false)
        }

        private fun indexOf(data: ByteArray, needle: ByteArray): Int {
            outer@ for (i in 0..(data.size - needle.size)) {
                for (j in needle.indices) {
                    if (data[i + j] != needle[j]) continue@outer
                }
                return i
            }
            return -1
        }
    }
}

/**
 * Downloads / installs sherpa-onnx Piper packs and normalizes incomplete zips
 * (generate tokens.txt from *.onnx.json, share espeak-ng-data across voices).
 */
class OfflineVoiceRepository(
    private val app: Context,
    private val api: HearTextApi,
    private val auth: AuthTokenProvider
) {
    private val root: File
        get() = File(app.filesDir, "offline_voices").also { it.mkdirs() }

    private val sharedDir: File
        get() = File(root, "_shared").also { it.mkdirs() }

    private val sharedEspeakDir: File
        get() = File(sharedDir, "espeak-ng-data")

    private val espeakMutex = Mutex()
    private val sampleMutex = Mutex()
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
        val dirs = root.listFiles()?.filter { it.isDirectory && it.name != "_shared" } ?: return emptyList()
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
        val dir = File(root, voiceId)
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
        val meta = readMeta(voiceId)
        return OfflineVoicePack(
            voiceId = voiceId,
            modelOnnx = onnx,
            tokens = tokens,
            lexicon = lexicon,
            dataDir = dataDir,
            sampleRate = meta?.sampleRate ?: 22050
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
        val zip = File(root, "${voice.id}.zip")
        api.downloadOfflineVoice(voice.id, zip, onProgress)
        val outDir = File(root, voice.id).also {
            if (it.exists()) it.deleteRecursively()
            it.mkdirs()
        }
        unzipTo(zip, outDir)
        zip.delete()
        writeMeta(voice)
        ensureSharedEspeakNgData()
        normalizePackDir(outDir)
        val pack = resolvePack(voice.id)
        if (pack == null || !pack.isPlayable) {
            val missing = buildList {
                if (pack?.modelOnnx == null) add(".onnx")
                if (pack?.tokens == null) add("tokens.txt")
                if (pack?.dataDir == null || !isEspeakDataDir(pack.dataDir)) add("espeak-ng-data")
                if (pack?.modelOnnx != null &&
                    !OfflineVoicePack.hasSherpaOnnxMetadata(pack.modelOnnx)
                ) {
                    add("sherpa ONNX metadata(sample_rate)")
                }
            }
            error(
                "语音包不完整（缺 ${missing.joinToString(" / ")}）。" +
                    "原始 Piper .onnx 需用 sherpa-onnx 转换后再上传。"
            )
        }
        outDir
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
        sampleMutex.withLock {
            stopSampleLocked()
            val sample = File(root, "${voiceId}_sample.mp3")
            if (!sample.exists() || sample.length() == 0L) {
                api.downloadOfflineVoiceSample(voiceId, sample)
            }
            val player = MediaPlayer()
            try {
                player.setDataSource(sample.absolutePath)
                player.setOnCompletionListener {
                    stopSample()
                    onFinished?.invoke()
                }
                player.setOnErrorListener { _, _, _ ->
                    stopSample()
                    onFinished?.invoke()
                    true
                }
                player.prepare()
                samplePlayer = player
                player.start()
            } catch (t: Throwable) {
                runCatching { player.release() }
                if (samplePlayer === player) samplePlayer = null
                throw t
            }
        }
    }

    fun stopSample() {
        // Avoid deadlock if called from MediaPlayer callbacks while holding sampleMutex.
        if (sampleMutex.tryLock()) {
            try {
                stopSampleLocked()
            } finally {
                sampleMutex.unlock()
            }
        } else {
            // Best-effort stop without waiting for playSample's lock.
            runCatching {
                samplePlayer?.stop()
                samplePlayer?.release()
            }
            samplePlayer = null
        }
    }

    private fun stopSampleLocked() {
        val player = samplePlayer
        samplePlayer = null
        if (player == null) return
        runCatching {
            if (player.isPlaying) player.stop()
        }
        runCatching { player.release() }
    }

    private fun unzipTo(zip: File, outDir: File) {
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val relative = entry.name
                    .replace('\\', '/')
                    .trimStart('/')
                    .takeIf { it.isNotBlank() && !it.contains("..") }
                if (relative != null && !entry.isDirectory) {
                    val target = File(outDir, relative)
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        flattenSingleRootIfNeeded(outDir)
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
            Log.w(TAG, "Failed to generate tokens.txt from ${json.name}: ${it.message}")
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

    private fun downloadAndExtractEspeakNgData() {
        sharedEspeakDir.parentFile?.mkdirs()
        if (sharedEspeakDir.exists()) sharedEspeakDir.deleteRecursively()
        val archive = File(sharedDir, "espeak-ng-data.tar.bz2")
        val url = URI(ESPEAK_DATA_URL).toURL()
        Log.i(TAG, "Downloading shared espeak-ng-data…")
        url.openStream().use { input ->
            archive.outputStream().use { output -> input.copyTo(output) }
        }
        require(archive.length() > 100_000L) { "espeak-ng-data download failed" }
        extractTarBz2(archive, sharedDir)
        archive.delete()
        // Tarball usually contains top-level espeak-ng-data/
        if (!isEspeakDataDir(sharedEspeakDir)) {
            val nested = sharedDir.walkTopDown()
                .firstOrNull { it.isDirectory && it.name.equals("espeak-ng-data", ignoreCase = true) }
            if (nested != null && nested != sharedEspeakDir) {
                nested.copyRecursively(sharedEspeakDir, overwrite = true)
            }
        }
        require(isEspeakDataDir(sharedEspeakDir)) {
            "espeak-ng-data extract failed"
        }
        Log.i(TAG, "Shared espeak-ng-data ready at ${sharedEspeakDir.absolutePath}")
    }

    private fun extractTarBz2(archive: File, destDir: File) {
        destDir.mkdirs()
        BufferedInputStream(archive.inputStream()).use { fileIn ->
            BZip2CompressorInputStream(fileIn).use { bzIn ->
                TarArchiveInputStream(bzIn).use { tarIn ->
                    var entry = tarIn.nextEntry
                    while (entry != null) {
                        val name = entry.name.replace('\\', '/').trimStart('/')
                        if (name.isNotBlank() && !name.contains("..")) {
                            val out = File(destDir, name)
                            if (entry.isDirectory) {
                                out.mkdirs()
                            } else {
                                out.parentFile?.mkdirs()
                                out.outputStream().use { tarIn.copyTo(it) }
                            }
                        }
                        entry = tarIn.nextEntry
                    }
                }
            }
        }
    }

    private fun isEspeakDataDir(dir: File?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        // phontab is present in standard espeak-ng-data trees
        return File(dir, "phontab").isFile ||
            File(dir, "lang").isDirectory ||
            dir.list()?.isNotEmpty() == true && File(dir, "voices").exists()
    }

    private fun writeMeta(voice: ApiOfflineVoice) {
        val meta = JSONObject()
            .put("id", voice.id)
            .put("name", voice.name)
            .put("language", voice.language)
            .put("engine", voice.engine)
            .put("model_type", voice.modelType)
            .put("sample_rate", voice.sampleRate)
        File(root, voice.id).resolve("heartext_meta.json").writeText(meta.toString())
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
