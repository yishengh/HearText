package com.yishenghuang.heartext

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Typeface
import androidx.test.core.app.ApplicationProvider
import com.yishenghuang.heartext.data.FontStore
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID

class FontStoreTest {
    @Test fun validFontsUseDistinctPathsAndFailedOrCancelledImportsPreserveThem() = runBlocking<Unit> {
        val app = ApplicationProvider.getApplicationContext<Context>()
        check(BuildConfig.APPLICATION_ID.endsWith(".validation"))
        val root = File(app.cacheDir, "font-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getFilesDir() = root }
        val store = FontStore(context)
        try {
            val first = store.importStream { app.assets.open("fonts/Lora-Regular.ttf") }!!
            val bytes = File(first).readBytes()
            val second = store.importStream { app.assets.open("fonts/SourceSans3-Regular.ttf") }!!
            assertNotEquals(first, second)
            assertNotNull(Typeface.Builder(File(second)).build())
            assertEquals(first, store.importStream { app.assets.open("fonts/Lora-Regular.ttf") })
            assertNull(store.importStream { ByteArrayInputStream("not a font".toByteArray()) })
            assertNull(store.importStream { ByteArrayInputStream(byteArrayOf()) })
            val cancelled = launch {
                val current = currentCoroutineContext()[Job]!!
                store.importStream {
                    object : ByteArrayInputStream(bytes) {
                        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                            current.cancel()
                            return super.read(buffer, offset, length)
                        }
                    }
                }
            }
            cancelled.join()
            assertTrue(cancelled.isCancelled)
            assertArrayEquals(bytes, File(first).readBytes())
            assertEquals(2, File(root, "fonts").listFiles()!!.size)
            val unavailable = File(root, "not-a-directory").apply { writeText("occupied") }
            val unavailableContext = object : ContextWrapper(app) { override fun getFilesDir() = unavailable }
            assertNull(FontStore(unavailableContext).importStream { ByteArrayInputStream(bytes) })
        } finally { root.deleteRecursively() }
    }
}
