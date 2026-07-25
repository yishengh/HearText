package com.yishenghuang.heartext.data

import android.content.Context
import com.yishenghuang.heartext.network.ApiCatalogBook
import com.yishenghuang.heartext.network.ApiCatalogPreview
import com.yishenghuang.heartext.network.ApiCatalogToc
import com.yishenghuang.heartext.network.AuthTokenProvider
import com.yishenghuang.heartext.network.HearTextApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CatalogRepository(
    private val app: Context,
    private val api: HearTextApi,
    private val auth: AuthTokenProvider,
    private val bookDao: BookDao,
    private val coverStore: CoverStore,
    private val cloudSync: CloudSyncRepository
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

    /**
     * Download EPUB via stream, register on shelf, and upsert local book row.
     */
    suspend fun downloadAndShelf(catalog: ApiCatalogBook): BookEntity = withContext(Dispatchers.IO) {
        requireSignedIn()
        val dest = File(catalogDir, "${catalog.id}.epub")
        api.downloadCatalogBook(catalog.id, dest)
        val shelf = api.addCatalogToShelf(catalog.id)
        val localId = shelf.book.clientBookId?.takeIf { it.isNotBlank() }
            ?: "catalog_${catalog.id}"
        val title = shelf.book.title
        val author = shelf.book.author ?: catalog.author ?: "Unknown"
        val remoteCover = shelf.book.coverUrl?.takeIf { it.isNotBlank() }
            ?: catalog.coverUrl?.takeIf { it.isNotBlank() }
        val parsed = runCatching { EpubParser.parse(dest) }.getOrNull()
        val (coverPath, coverSource) = coverStore.resolve(
            bookId = localId,
            title = title,
            author = author,
            remoteUrl = remoteCover,
            epubBytes = parsed?.coverBytes
        )
        val entity = BookEntity(
            id = localId,
            title = title,
            author = author,
            format = BookFormat.EPUB,
            filePath = dest.absolutePath,
            coverPath = coverPath,
            coverUrl = remoteCover,
            coverSource = coverSource,
            remoteBookId = shelf.book.id,
            catalogBookId = catalog.id,
            description = catalog.description?.takeIf { it.isNotBlank() },
            totalChapters = parsed?.chapters?.size?.coerceAtLeast(1) ?: 1,
            source = BookSource.CATALOG,
            progressUpdatedAt = System.currentTimeMillis()
        )
        bookDao.upsert(entity)
        runCatching { cloudSync.ensureRemoteBook(entity) }
        entity
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
