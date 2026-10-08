package com.yishenghuang.heartext

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yishenghuang.heartext.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class BookStorageTest {
    private lateinit var root: File
    private lateinit var database: AppDatabase
    private lateinit var repository: BookRepository
    private lateinit var preferencesName: String

    @Before fun setUp() {
        check(BuildConfig.APPLICATION_ID.endsWith(".validation"))
        val app = ApplicationProvider.getApplicationContext<Context>()
        root = File(app.cacheDir, "book-test-${UUID.randomUUID()}").apply { mkdirs() }
        preferencesName = "book-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(app) {
            override fun getFilesDir() = File(root, "private").apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) =
                app.getSharedPreferences("$preferencesName-$name", mode)
        }
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        repository = BookRepository(context, database.bookDao(), CoverStore(context), annotationDao = database.annotationDao())
    }

    @After fun tearDown() {
        database.close()
        ApplicationProvider.getApplicationContext<Context>()
            .deleteSharedPreferences("$preferencesName-library_state")
        root.deleteRecursively()
    }

    @Test fun importTextPdfAndEpubThenDeleteBookAndAnnotations() = runBlocking {
        val txt = File(root, "sample.txt").apply { writeText("Preface\nChapter 1\nOne\nChapter 2\nTwo") }
        val textBook = repository.importFromUri(Uri.fromFile(txt))
        assertEquals(2, textBook.totalChapters)
        assertTrue(repository.loadChapterTexts(textBook).first().plainText.startsWith("Preface"))

        val pdf = File(root, "sample.pdf")
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(200, 200, 1).create())
            page.canvas.drawText("Local PDF", 20f, 50f, Paint())
            document.finishPage(page)
            pdf.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
        assertEquals(BookFormat.PDF, repository.importFromUri(Uri.fromFile(pdf)).format)
        val epub = File(root, "sample.epub")
        ApplicationProvider.getApplicationContext<Context>().assets.open("samples/alice_in_wonderland.epub").use { input ->
            epub.outputStream().use(input::copyTo)
        }
        assertEquals(BookFormat.EPUB, repository.importFromUri(Uri.fromFile(epub)).format)
        database.annotationDao().upsert(AnnotationEntity("bookmark", textBook.id, "bookmark", "bookmark"))
        repository.deleteBook(textBook.id)
        assertNull(repository.getBook(textBook.id))
        assertFalse(File(textBook.filePath).exists())
        assertTrue(database.annotationDao().listForBook(textBook.id).isEmpty())
        assertEquals(2, database.bookDao().count())
    }

    @Test fun invalidImportsLeaveNoBooksOrCopiedFiles() = runBlocking {
        for ((name, bytes) in mapOf(
            "broken.epub" to "not a zip".toByteArray(),
            "broken.pdf" to "not a PDF".toByteArray(),
            "binary.txt" to byteArrayOf(0, 1, 2),
            "empty.txt" to byteArrayOf()
        )) {
            val file = File(root, name).apply { writeBytes(bytes) }
            val failure = runCatching { repository.importFromUri(Uri.fromFile(file)) }.exceptionOrNull()
            assertNotNull("$name must fail", failure)
            assertEquals(0, database.bookDao().count())
            assertTrue(File(root, "private/books").listFiles().orEmpty().isEmpty())
        }
    }

    @Test fun metadataChangesCannotOverwriteProgressAndOlderProgressIsIgnored() = runBlocking {
        val dao = database.bookDao()
        val book = BookEntity("book", "Title", "Author", BookFormat.TXT, "unused")
        dao.upsert(book)
        dao.updateProgress(book.id, 2, 4, 35f, 2000, "locator", 10)
        dao.updateCover(book.id, "new-cover", CoverSource.USER)
        dao.updateDescription(book.id, "New description")
        dao.updateChapterCount(book.id, 11)
        dao.updateProgress(book.id, 0, 0, 0f, 1000, null, null)
        val stored = dao.getBook(book.id)!!
        assertEquals(2, stored.lastChapterIndex)
        assertEquals(4, stored.lastOffset)
        assertEquals(35f, stored.progressPercent)
        assertEquals("locator", stored.locatorJson)
        assertEquals("new-cover", stored.coverPath)
        assertEquals("New description", stored.description)
        assertEquals(11, stored.totalChapters)
    }
}
