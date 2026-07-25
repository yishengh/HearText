package com.yishenghuang.heartext.data

import com.yishenghuang.heartext.network.AuthTokenProvider
import com.yishenghuang.heartext.network.HearTextApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter

class CloudSyncRepository(
    private val bookDao: BookDao,
    private val api: HearTextApi,
    private val auth: AuthTokenProvider,
    private val coverStore: CoverStore
) {
    suspend fun ensureRemoteBook(book: BookEntity): BookEntity = withContext(Dispatchers.IO) {
        if (!auth.isSignedIn || !api.isConfigured) return@withContext book
        if (!book.remoteBookId.isNullOrBlank()) {
            return@withContext maybeApplyRemoteCover(book)
        }
        // Prefer matching existing remote by client_book_id
        val existing = runCatching { api.listBooks() }.getOrNull()
            ?.firstOrNull { it.clientBookId == book.id }
        if (existing != null) {
            var updated = book.copy(
                remoteBookId = existing.id,
                coverUrl = existing.coverUrl ?: book.coverUrl
            )
            updated = applyCoverFromRemote(updated, existing.coverUrl)
            bookDao.update(updated)
            return@withContext updated
        }
        val created = api.createBook(
            clientBookId = book.id,
            title = book.title,
            author = book.author,
            format = book.format.name.lowercase(),
            coverUrl = book.coverUrl
        )
        var updated = book.copy(
            remoteBookId = created.id,
            coverUrl = created.coverUrl ?: book.coverUrl
        )
        updated = applyCoverFromRemote(updated, created.coverUrl)
        bookDao.update(updated)
        updated
    }

    private suspend fun maybeApplyRemoteCover(book: BookEntity): BookEntity {
        val remoteId = book.remoteBookId ?: return book
        if (book.coverSource == CoverSource.USER && coverStore.hasUsableCover(book.coverPath)) {
            return book
        }
        if (coverStore.hasUsableCover(book.coverPath) &&
            book.coverSource != null &&
            book.coverSource != CoverSource.GENERATED
        ) {
            return book
        }
        val remote = runCatching {
            api.listBooks().firstOrNull { it.id == remoteId }
        }.getOrNull() ?: return book
        val url = remote.coverUrl?.takeIf { it.isNotBlank() } ?: return book
        return applyCoverFromRemote(book.copy(coverUrl = url), url).also {
            bookDao.update(it)
        }
    }

    private suspend fun applyCoverFromRemote(book: BookEntity, remoteUrl: String?): BookEntity {
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
        return book.copy(
            coverPath = path,
            coverUrl = remoteUrl,
            coverSource = source
        )
    }

    suspend fun pushProgress(book: BookEntity) = withContext(Dispatchers.IO) {
        if (!auth.isSignedIn || !api.isConfigured) return@withContext
        val synced = ensureRemoteBook(book)
        val remoteId = synced.remoteBookId ?: return@withContext
        val updatedAt = if (synced.progressUpdatedAt > 0L) {
            synced.progressUpdatedAt
        } else {
            System.currentTimeMillis()
        }
        api.putProgress(
            bookId = remoteId,
            chapterId = null,
            chapterIndex = synced.lastChapterIndex,
            position = synced.lastOffset,
            percentage = synced.progressPercent.toDouble(),
            clientUpdatedAtIso = toIso(updatedAt)
        )
    }

    suspend fun pullAndMergeProgress() = withContext(Dispatchers.IO) {
        if (!auth.isSignedIn || !api.isConfigured) return@withContext
        val remoteBooks = api.listBooks()
        for (remote in remoteBooks) {
            val localId = remote.clientBookId ?: continue
            val local = bookDao.getBook(localId) ?: continue
            var updated = local
            if (local.remoteBookId != remote.id) {
                updated = updated.copy(remoteBookId = remote.id)
            }
            if (!remote.coverUrl.isNullOrBlank()) {
                updated = applyCoverFromRemote(
                    updated.copy(coverUrl = remote.coverUrl),
                    remote.coverUrl
                )
            }
            if (updated != local) {
                bookDao.update(updated)
            }
        }
        val progressList = api.listProgress()
        for (p in progressList) {
            val local = bookDao.getBookByRemoteId(p.bookId) ?: continue
            val remoteMillis = parseIso(p.clientUpdatedAt) ?: 0L
            if (remoteMillis >= local.progressUpdatedAt) {
                bookDao.update(
                    local.copy(
                        lastChapterIndex = p.chapterIndex,
                        lastOffset = p.position,
                        progressPercent = p.percentage.toFloat().coerceIn(0f, 100f),
                        progressUpdatedAt = remoteMillis
                    )
                )
            }
        }
    }

    suspend fun pushAllLocalBooks() = withContext(Dispatchers.IO) {
        if (!auth.isSignedIn || !api.isConfigured) return@withContext
        bookDao.getAll().forEach { book ->
            runCatching { ensureRemoteBook(book) }
            if (book.progressUpdatedAt > 0L || book.progressPercent > 0f) {
                runCatching { pushProgress(bookDao.getBook(book.id) ?: book) }
            }
        }
    }

    suspend fun deleteRemoteBook(remoteBookId: String) = withContext(Dispatchers.IO) {
        if (!auth.isSignedIn || !api.isConfigured) return@withContext
        runCatching { api.deleteBook(remoteBookId) }
    }

    private fun toIso(epochMillis: Long): String {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMillis))
    }

    private fun parseIso(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    }
}
