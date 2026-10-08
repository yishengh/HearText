package com.yishenghuang.heartext

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import com.yishenghuang.heartext.data.StorageRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoilApi::class)
class StorageRepositoryTest {
    @Test fun cacheOwnerClearsEntriesWhileKeepingActiveSnapshotsAndUserFiles() = runBlocking {
        check(BuildConfig.APPLICATION_ID.endsWith(".validation"))
        val app = ApplicationProvider.getApplicationContext<Context>()
        val root = File(app.cacheDir, "storage-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getDataDir() = root
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        }
        val disk = DiskCache.Builder().directory(File(context.cacheDir, "images")).maxSizeBytes(1024 * 1024).build()
        val images = ImageLoader.Builder(context).diskCache(disk).memoryCache(null).build()
        fun fixture(relative: String, bytes: Int): File = File(root, relative).apply {
            parentFile!!.mkdirs(); writeBytes(ByteArray(bytes) { 7 })
        }
        val retained = listOf(fixture("files/books/book.epub", 10), fixture("files/covers/cover.jpg", 20),
            fixture("files/fonts/font.ttf", 30), fixture("files/offline_voices/model.onnx", 40),
            fixture("cache/readium/open-publication", 50), fixture("files/catalog/.tmp/active-download", 60),
            fixture("databases/book.db", 70), fixture("shared_prefs/preferences.xml", 80))
        fun cache(key: String, bytes: Int) {
            val editor = requireNotNull(disk.openEditor(key))
            disk.fileSystem.write(editor.data) { write(ByteArray(bytes) { 9 }) }
            disk.fileSystem.write(editor.metadata) { writeUtf8("metadata") }
            editor.commit()
        }
        try {
            cache("unused", 1024)
            cache("reading", 2048)
            val held = requireNotNull(disk.openSnapshot("reading"))
            try {
                val repository = StorageRepository(context, images)
                val usage = repository.measure()
                assertEquals(10L, usage.books)
                assertEquals(20L, usage.covers)
                assertEquals(30L, usage.fonts)
                assertEquals(40L, usage.voices)
                assertEquals(60L, usage.catalog)
                assertEquals(150L, usage.other)
                assertTrue(usage.cache >= 50 + 1024 + 2048)
                repository.clearImageCache()
                assertNull(disk.openSnapshot("unused"))
                assertEquals(2048L, disk.fileSystem.metadata(held.data).size)
                retained.forEach { assertTrue(it.path, it.exists()); assertEquals(7, it.readBytes()[0].toInt()) }
            } finally { held.close() }
            StorageRepository(context, images).clearImageCache()
            assertNull(disk.openSnapshot("reading"))
        } finally {
            images.shutdown()
            root.deleteRecursively()
        }
    }
}
