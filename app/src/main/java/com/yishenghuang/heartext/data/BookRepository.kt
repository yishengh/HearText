package com.yishenghuang.heartext.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class BookRepository(
    private val context: Context,
    private val bookDao: BookDao,
    private val coverStore: CoverStore,
    private val cloudSync: CloudSyncRepository? = null
) {
    private val booksDir: File
        get() = File(context.filesDir, "books").also { it.mkdirs() }

    fun observeBooks(): Flow<List<BookEntity>> = bookDao.observeBooks()

    fun observeBook(id: String): Flow<BookEntity?> = bookDao.observeBook(id)

    suspend fun getBook(id: String): BookEntity? = bookDao.getBook(id)

    suspend fun getMostRecent(): BookEntity? = bookDao.getMostRecent()

    suspend fun ensureSampleBooks() = withContext(Dispatchers.IO) {
        ensureAliceBook()
        if (bookDao.count() > 0) {
            ensureMissingCovers()
            return@withContext
        }
        val sampleTxt = File(booksDir, "sample_welcome.txt")
        if (!sampleTxt.exists()) {
            sampleTxt.writeText(
                """
                Welcome to HearText

                This is a sample plain-text book so you can try reading and text-to-speech right away.

                Import your own EPUB or TXT files from the Library screen using the Add button.

                Chapter ideas
                HearText lets you keep a personal library on your device. Open a book, adjust the reading theme, and tap Listen to hear the text spoken aloud.

                You can download offline voice packs in Profile, or use the built-in system TTS.
                """.trimIndent()
            )
        }
        val id = UUID.randomUUID().toString()
        val title = "Welcome to HearText"
        val author = "HearText"
        val (coverPath, coverSource) = coverStore.resolve(
            bookId = id,
            title = title,
            author = author
        )
        val book = BookEntity(
            id = id,
            title = title,
            author = author,
            format = BookFormat.TXT,
            filePath = sampleTxt.absolutePath,
            coverPath = coverPath,
            coverSource = coverSource,
            totalChapters = 1,
            progressPercent = 0f
        )
        bookDao.upsert(book)
    }

    /**
     * Ships a real public-domain EPUB (Alice) and syncs it to the backend shelf when signed in.
     * Uses a stable client_book_id so cloud sync can match Open Library / seed fixtures.
     */
    private suspend fun ensureAliceBook() {
        val aliceId = "OL138052W"
        val dest = File(booksDir, "alice_in_wonderland.epub")
        if (!dest.exists()) {
            try {
                context.assets.open("samples/alice_in_wonderland.epub").use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (_: Exception) {
                return
            }
        }
        val existing = bookDao.getBook(aliceId)
        if (existing != null) {
            runCatching { cloudSync?.ensureRemoteBook(existing) }
            return
        }
        val parsed = runCatching { EpubParser.parse(dest) }.getOrNull()
        val title = parsed?.title?.takeIf { it.isNotBlank() && !it.equals("11", true) }
            ?: "Alice's Adventures in Wonderland"
        val author = parsed?.author?.takeIf { it.isNotBlank() && it != "Unknown" }
            ?: "Lewis Carroll"
        val (coverPath, coverSource) = coverStore.resolve(
            bookId = aliceId,
            title = title,
            author = author,
            epubBytes = parsed?.coverBytes
        )
        val book = BookEntity(
            id = aliceId,
            title = title,
            author = author,
            format = BookFormat.EPUB,
            filePath = dest.absolutePath,
            coverPath = coverPath,
            coverSource = coverSource,
            totalChapters = parsed?.chapters?.size?.coerceAtLeast(1) ?: 1
        )
        bookDao.upsert(book)
        runCatching { cloudSync?.ensureRemoteBook(book) }
    }

    /** Backfill covers for books that still have none. */
    suspend fun ensureMissingCovers() = withContext(Dispatchers.IO) {
        bookDao.getAll().forEach { book ->
            if (coverStore.hasUsableCover(book.coverPath)) return@forEach
            val epubBytes = if (book.format == BookFormat.EPUB) {
                runCatching { EpubParser.parse(File(book.filePath)).coverBytes }.getOrNull()
            } else {
                null
            }
            val (path, source) = coverStore.resolve(
                bookId = book.id,
                title = book.title,
                author = book.author,
                remoteUrl = book.coverUrl,
                epubBytes = epubBytes,
                existingPath = book.coverPath,
                existingSource = book.coverSource
            )
            bookDao.update(
                book.copy(
                    coverPath = path,
                    coverSource = source
                )
            )
        }
    }

    suspend fun importFromUri(uri: Uri): BookEntity = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri) ?: "book_${System.currentTimeMillis()}"
        val lower = displayName.lowercase()
        val format = when {
            lower.endsWith(".epub") -> BookFormat.EPUB
            lower.endsWith(".pdf") -> BookFormat.PDF
            lower.endsWith(".txt") -> BookFormat.TXT
            else -> {
                val mime = context.contentResolver.getType(uri).orEmpty()
                when {
                    mime.contains("epub") -> BookFormat.EPUB
                    mime.contains("pdf") -> BookFormat.PDF
                    else -> BookFormat.TXT
                }
            }
        }
        val ext = when (format) {
            BookFormat.EPUB -> "epub"
            BookFormat.PDF -> "pdf"
            BookFormat.TXT -> "txt"
        }
        val id = UUID.randomUUID().toString()
        val dest = File(booksDir, "$id.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to read selected file")
        importLocalFile(dest, preferredTitle = displayName.substringBeforeLast('.'), id = id)
    }

    private suspend fun importLocalFile(
        file: File,
        preferredTitle: String? = null,
        id: String = UUID.randomUUID().toString()
    ): BookEntity {
        return when {
            file.extension.equals("epub", ignoreCase = true) -> {
                val parsed = EpubParser.parse(file)
                val title = preferredTitle?.takeIf { it.isNotBlank() } ?: parsed.title
                val author = parsed.author
                val (coverPath, coverSource) = coverStore.resolve(
                    bookId = id,
                    title = title,
                    author = author,
                    epubBytes = parsed.coverBytes
                )
                val book = BookEntity(
                    id = id,
                    title = title,
                    author = author,
                    format = BookFormat.EPUB,
                    filePath = file.absolutePath,
                    coverPath = coverPath,
                    coverSource = coverSource,
                    totalChapters = parsed.chapters.size.coerceAtLeast(1)
                )
                bookDao.upsert(book)
                runCatching { cloudSync?.ensureRemoteBook(book) }
                book
            }
            file.extension.equals("pdf", ignoreCase = true) -> {
                val title = preferredTitle?.takeIf { it.isNotBlank() }
                    ?: file.nameWithoutExtension
                val author = "Unknown"
                val (coverPath, coverSource) = coverStore.resolve(
                    bookId = id,
                    title = title,
                    author = author
                )
                val book = BookEntity(
                    id = id,
                    title = title,
                    author = author,
                    format = BookFormat.PDF,
                    filePath = file.absolutePath,
                    coverPath = coverPath,
                    coverSource = coverSource,
                    totalChapters = 1
                )
                bookDao.upsert(book)
                runCatching { cloudSync?.ensureRemoteBook(book) }
                book
            }
            else -> {
                val title = preferredTitle?.takeIf { it.isNotBlank() }
                    ?: TextBookLoader.titleFromFile(file)
                val author = "Unknown"
                val (coverPath, coverSource) = coverStore.resolve(
                    bookId = id,
                    title = title,
                    author = author
                )
                val book = BookEntity(
                    id = id,
                    title = title,
                    author = author,
                    format = BookFormat.TXT,
                    filePath = file.absolutePath,
                    coverPath = coverPath,
                    coverSource = coverSource,
                    totalChapters = 1
                )
                bookDao.upsert(book)
                runCatching { cloudSync?.ensureRemoteBook(book) }
                book
            }
        }
    }

    suspend fun setUserCover(bookId: String, uri: Uri): BookEntity? = withContext(Dispatchers.IO) {
        val book = bookDao.getBook(bookId) ?: return@withContext null
        val path = coverStore.saveUserCover(bookId, uri) ?: return@withContext null
        val updated = book.copy(
            coverPath = path,
            coverSource = CoverSource.USER
        )
        bookDao.update(updated)
        // Keep remote URL if present; local USER cover wins for display.
        updated
    }

    suspend fun updateDescription(bookId: String, description: String?): BookEntity? = withContext(Dispatchers.IO) {
        val book = bookDao.getBook(bookId) ?: return@withContext null
        val text = description?.takeIf { it.isNotBlank() } ?: return@withContext book
        if (book.description == text) return@withContext book
        val updated = book.copy(description = text)
        bookDao.update(updated)
        updated
    }

    suspend fun applyRemoteCover(book: BookEntity, remoteUrl: String?): BookEntity {
        if (remoteUrl.isNullOrBlank()) return book
        if (book.coverSource == CoverSource.USER && coverStore.hasUsableCover(book.coverPath)) {
            return book.copy(coverUrl = remoteUrl)
        }
        val (path, source) = coverStore.resolve(
            bookId = book.id,
            title = book.title,
            author = book.author,
            remoteUrl = remoteUrl,
            existingPath = book.coverPath,
            existingSource = book.coverSource
        )
        val updated = book.copy(
            coverPath = path,
            coverUrl = remoteUrl,
            coverSource = source
        )
        bookDao.update(updated)
        return updated
    }

    suspend fun updateProgress(
        bookId: String,
        chapterIndex: Int,
        offset: Int,
        progressPercent: Float,
        locatorJson: String? = null,
        totalChapters: Int? = null
    ) = withContext(Dispatchers.IO) {
        val book = bookDao.getBook(bookId) ?: return@withContext
        val updated = book.copy(
            lastChapterIndex = chapterIndex,
            lastOffset = offset,
            progressPercent = progressPercent.coerceIn(0f, 100f),
            progressUpdatedAt = System.currentTimeMillis(),
            locatorJson = locatorJson ?: book.locatorJson,
            totalChapters = totalChapters?.coerceAtLeast(1) ?: book.totalChapters
        )
        bookDao.update(updated)
        runCatching { cloudSync?.pushProgress(updated) }
    }

    /** Backfills [BookEntity.totalChapters] for rows saved before the count was known. */
    suspend fun ensureChapterCount(bookId: String) = withContext(Dispatchers.IO) {
        val book = bookDao.getBook(bookId) ?: return@withContext
        if (book.totalChapters > 1 || book.format == BookFormat.PDF) return@withContext
        val count = runCatching { loadChapterTexts(book).size }.getOrDefault(0)
        if (count <= 1) return@withContext
        bookDao.update(book.copy(totalChapters = count))
    }

    suspend fun syncAfterImport(book: BookEntity) {
        runCatching { cloudSync?.ensureRemoteBook(book) }
    }

    suspend fun syncOnLogin() {
        runCatching {
            cloudSync?.pushAllLocalBooks()
            cloudSync?.pullAndMergeProgress()
        }
        ensureMissingCovers()
    }

    private val chapterCache = ChapterCache()

    suspend fun loadChapterTexts(book: BookEntity): List<EpubChapter> = withContext(Dispatchers.IO) {
        when (book.format) {
            BookFormat.TXT -> {
                val file = File(book.filePath)
                val cached = chapterCache.get(file.path, file.length(), file.lastModified())
                if (cached != null) return@withContext cached
                val text = TextBookLoader.load(file)
                val chapters = TextBookLoader.splitChapters(book.title, text)
                chapterCache.put(file.path, file.length(), file.lastModified(), chapters)
                chapters
            }
            BookFormat.EPUB -> {
                val file = File(book.filePath)
                val cached = chapterCache.get(file.path, file.length(), file.lastModified())
                if (cached != null) return@withContext cached
                val chapters = EpubParser.parse(file).chapters
                chapterCache.put(file.path, file.length(), file.lastModified(), chapters)
                chapters
            }
            BookFormat.PDF -> emptyList()
        }
    }

    suspend fun deleteBook(bookId: String) = withContext(Dispatchers.IO) {
        val book = bookDao.getBook(bookId) ?: return@withContext
        bookDao.delete(bookId)
        runCatching { File(book.filePath).delete() }
        coverStore.delete(bookId)
        book.coverPath?.let { runCatching { File(it).delete() } }
        book.remoteBookId?.let { remoteId ->
            runCatching { cloudSync?.deleteRemoteBook(remoteId) }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return uri.lastPathSegment
    }
}
