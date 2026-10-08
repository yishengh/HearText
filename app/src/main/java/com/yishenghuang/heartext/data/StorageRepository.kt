package com.yishenghuang.heartext.data

import android.content.Context
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

internal data class StorageUsage(
    val books: Long, val covers: Long, val catalog: Long, val voices: Long,
    val fonts: Long, val cache: Long, val other: Long
) { val total get() = books + covers + catalog + voices + fonts + cache + other }

/** Only cache owners may remove their managed files; reader and SDK caches remain untouched. */
@OptIn(ExperimentalCoilApi::class)
internal class StorageRepository(private val context: Context, private val images: ImageLoader = context.imageLoader) {
    suspend fun measure(): StorageUsage = withContext(Dispatchers.IO) {
        val files = context.filesDir
        val coroutine = currentCoroutineContext()
        fun size(file: File): Long {
            coroutine.ensureActive()
            if (Files.isSymbolicLink(file.toPath()) || !file.exists()) return 0
            if (file.isFile) return file.length()
            return file.listFiles()?.sumOf { size(it) } ?: 0
        }
        val known = setOf("books", "covers", "catalog", "offline_voices", "voices", "sherpa", "fonts")
        StorageUsage(
            size(File(files, "books")), size(File(files, "covers")), size(File(files, "catalog")),
            listOf("offline_voices", "voices", "sherpa").sumOf { size(File(files, it)) },
            size(File(files, "fonts")), size(context.cacheDir),
            (files.listFiles()?.filter { it.name !in known }?.sumOf { size(it) } ?: 0) +
                size(File(context.dataDir, "databases")) + size(File(context.dataDir, "shared_prefs"))
        )
    }

    suspend fun clearImageCache(): Long = withContext(Dispatchers.IO) {
        val cache = images.diskCache
        val before = cache?.size ?: 0
        images.memoryCache?.clear()
        cache?.clear()
        (before - (cache?.size ?: 0)).coerceAtLeast(0)
    }
}
