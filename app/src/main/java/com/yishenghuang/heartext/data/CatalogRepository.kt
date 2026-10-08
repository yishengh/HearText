package com.yishenghuang.heartext.data

import android.content.Context
import com.yishenghuang.heartext.network.ApiCatalogBook
import com.yishenghuang.heartext.network.ApiCatalogPreview
import com.yishenghuang.heartext.network.ApiCatalogToc
import com.yishenghuang.heartext.network.SessionTokenProvider
import com.yishenghuang.heartext.network.ExpectedSession
import com.yishenghuang.heartext.network.requestSession
import com.yishenghuang.heartext.network.requireSession
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import com.yishenghuang.heartext.network.HearTextApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CatalogRepository(
    private val app: Context,
    private val api: HearTextApi,
    private val auth: SessionTokenProvider,
    private val bookDao: BookDao,
    private val coverStore: CoverStore
) {
    private val catalogDir: File
        get() = File(app.filesDir, "catalog").also { it.mkdirs() }

    suspend fun featured() = withContext(Dispatchers.IO) {
        requireSignedIn()
        api.listFeaturedCatalog()
    }

    suspend fun categories() = withContext(Dispatchers.IO) {
        requireSignedIn()
        api.listCatalogCategories()
    }

    suspend fun search(
        q: String? = null,
        category: String? = null,
        page: Int = 1,
        pageSize: Int = 50
    ) = withContext(Dispatchers.IO) {
        requireSignedIn()
        api.listCatalog(q = q, category = category, page = page, pageSize = pageSize)
    }

    suspend fun rankings(metric: String = "shelves") = withContext(Dispatchers.IO) {
        requireSignedIn()
        api.listCatalogRankings(metric)
    }

    suspend fun detail(catalogId: String): ApiCatalogBook = withContext(Dispatchers.IO) {
        requireSignedIn()
        api.getCatalogBook(catalogId)
    }

    suspend fun toc(catalogId: String): ApiCatalogToc = withContext(Dispatchers.IO) {
        requireSignedIn()
        api.getCatalogToc(catalogId)
    }

    suspend fun preview(catalogId: String): ApiCatalogPreview = withContext(Dispatchers.IO) {
        requireSignedIn()
        api.getCatalogPreview(catalogId)
    }


    private val downloads = Mutex()

    /** Validate a separate file before shelving; commit local metadata without resetting user data. */
    suspend fun downloadAndShelf(catalog: ApiCatalogBook): BookEntity =
        withContext(Dispatchers.IO + ExpectedSession(auth.requestSession())) {
            requireSignedIn()
            val session = auth.requestSession()
            val owner = requireNotNull(auth.accountId)
            downloads.withLock {
                auth.requireSession(session)
                val fileId = UUID.randomUUID().toString()
                val dest = File(catalogDir, "$fileId.epub")
                var coverPath: String? = null
                var retained = false
                try {
                    api.downloadCatalogBook(catalog.id, dest)
                    val parsed = EpubParser.parse(dest)
                    currentCoroutineContext().ensureActive()
                    auth.requireSession(session)
                    BookMutations.mutex.withLock {
                        auth.requireSession(session)
                        val shelf = api.addCatalogToShelf(catalog.id)
                        auth.requireSession(session)
                        val localId = shelf.book.clientBookId?.takeIf { it.isNotBlank() }
                            ?: "catalog_${catalog.id}"
                        val previous = bookDao.getBookByRemoteId(shelf.book.id)
                            ?: bookDao.getBook(localId)
                        val author = shelf.book.author ?: catalog.author.orEmpty()
                        val remoteCover = shelf.book.coverUrl?.takeIf { it.isNotBlank() }
                            ?: catalog.coverUrl?.takeIf { it.isNotBlank() }
                        val cover = coverStore.resolve(fileId, shelf.book.title, author,
                            remoteUrl = remoteCover, epubBytes = parsed.coverBytes)
                        coverPath = cover.first
                        val download = BookEntity(
                            id = localId, title = shelf.book.title, author = author,
                            format = BookFormat.EPUB, filePath = dest.absolutePath,
                            coverPath = cover.first, coverSource = cover.second, coverUrl = remoteCover,
                            remoteBookId = shelf.book.id, remoteOwnerId = owner,
                            catalogBookId = catalog.id, description = catalog.description,
                            totalChapters = parsed.chapters.size, source = BookSource.CATALOG
                        )
                        currentCoroutineContext().ensureActive()
                        auth.requireSession(session)
                        // Once the transaction starts, finish its bookkeeping even if the screen closes.
                        withContext(NonCancellable) {
                            val saved = bookDao.saveCatalogDownload(download)
                            retained = true
                            if (saved.coverPath != coverPath) { File(requireNotNull(coverPath)).delete(); coverPath = null }
                            runCatching { previous?.filePath?.let { oldPath ->
                                val old = File(oldPath)
                                if (old.canonicalFile.parentFile == catalogDir.canonicalFile &&
                                    oldPath != saved.filePath && bookDao.countFileReferences(oldPath) == 0) old.delete()
                            } }
                            saved
                        }
                    }
                } finally {
                    if (!retained) {
                        dest.delete()
                        coverStore.delete(fileId)
                        coverPath?.let { File(it).delete() }
                    }
                }
            }
        }

    private fun requireSignedIn() {
        if (!auth.isSignedIn || !api.isConfigured) {
            error("Sign in required for bookstore")
        }
    }
}

enum class BookSource {
    LOCAL,
    CATALOG
}
