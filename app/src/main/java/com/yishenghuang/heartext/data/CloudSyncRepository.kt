package com.yishenghuang.heartext.data

import com.yishenghuang.heartext.network.SessionTokenProvider
import com.yishenghuang.heartext.network.HearTextApi
import com.yishenghuang.heartext.network.requireSession
import com.yishenghuang.heartext.network.ExpectedSession
import com.yishenghuang.heartext.network.requestSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.Instant
import java.util.UUID

class CloudSyncRepository(
    private val bookDao: BookDao,
    private val api: HearTextApi,
    private val auth: SessionTokenProvider,
    private val coverStore: CoverStore
) {
    private val registration = Mutex()

    suspend fun ensureRemoteBook(book: BookEntity): BookEntity = withContext(Dispatchers.IO + ExpectedSession(auth.requestSession())) {
        val owner = auth.accountId ?: return@withContext book
        if (!api.isConfigured) return@withContext book
        val session = auth.requestSession()
        registration.withLock {
            auth.requireSession(session)
            val current = bookDao.getBook(book.id) ?: throw CancellationException("Book removed")
            if (current.remoteOwnerId != null && current.remoteOwnerId != owner) return@withLock current
            if (current.remoteOwnerId == owner && current.remoteBookId != null) return@withLock current
            // A failed listing is not evidence that this book does not exist.
            val remoteBooks = api.listBooks()
            auth.requireSession(session)
            val existing = remoteBooks.firstOrNull {
                if (current.remoteBookId != null) it.id == current.remoteBookId else it.clientBookId == current.id
            }
            // Legacy remote IDs require positive server evidence before assigning an owner.
            if (existing == null && current.remoteBookId != null) return@withLock current
            val remote = existing ?: api.createBook(current.id, current.title, current.author,
                current.format.name.lowercase(), current.coverUrl)
            auth.requireSession(session)
            bookDao.bindRemote(current.id, remote.id, owner, remote.coverUrl)
            applyRemoteCover(current.id, owner, remote.coverUrl, session)
            bookDao.getBook(current.id) ?: throw CancellationException("Book removed")
        }
    }

    private suspend fun applyRemoteCover(id: String, owner: String, url: String?, session: String?) {
        if (url.isNullOrBlank()) return
        val current = bookDao.getBook(id) ?: return
        if (current.remoteOwnerId != owner || current.coverSource == CoverSource.USER) return
        if (coverStore.hasUsableCover(current.coverPath) && current.coverSource != CoverSource.GENERATED) return
        // Never write over a file that a concurrent user-cover operation may be using.
        val path = coverStore.downloadRemote("remote_${UUID.randomUUID()}", url) ?: return
        var retained = false
        try {
            auth.requireSession(session)
            retained = bookDao.updateRemoteCover(id, owner, current.coverPath, path, CoverSource.REMOTE) == 1
        } finally {
            if (!retained) File(path).delete()
        }
    }

    suspend fun pushProgress(book: BookEntity) = withContext(Dispatchers.IO + ExpectedSession(auth.requestSession())) {
        val owner = auth.accountId ?: return@withContext
        if (!api.isConfigured) return@withContext
        val session = auth.requestSession()
        val synced = ensureRemoteBook(book)
        auth.requireSession(session)
        if (synced.remoteOwnerId != owner || synced.progressUpdatedAt <= 0) return@withContext
        val remoteId = synced.remoteBookId ?: return@withContext
        api.putProgress(remoteId, null, synced.lastChapterIndex, synced.lastOffset,
            synced.progressPercent.toDouble(), Instant.ofEpochMilli(synced.progressUpdatedAt).toString())
    }

    suspend fun pullAndMergeProgress() = withContext(Dispatchers.IO + ExpectedSession(auth.requestSession())) {
        val owner = auth.accountId ?: return@withContext
        if (!api.isConfigured) return@withContext
        val session = auth.requestSession()
        for (remote in api.listBooks()) {
            auth.requireSession(session)
            val local = bookDao.getBookByRemoteId(remote.id)
                ?: remote.clientBookId?.let { bookDao.getBook(it) } ?: continue
            if (bookDao.bindRemote(local.id, remote.id, owner, remote.coverUrl) != 1) continue
            applyRemoteCover(local.id, owner, remote.coverUrl, session)
        }
        auth.requireSession(session)
        for (progress in api.listProgress()) {
            auth.requireSession(session)
            val local = bookDao.getBookByRemoteId(progress.bookId) ?: continue
            val updatedAt = runCatching { Instant.parse(progress.clientUpdatedAt).toEpochMilli() }.getOrNull() ?: continue
            if (!progress.percentage.isFinite() || progress.chapterIndex < 0 || progress.position < 0) continue
            bookDao.mergeRemoteProgress(local.id, owner, progress.chapterIndex, progress.position,
                progress.percentage.toFloat().coerceIn(0f, 100f), updatedAt)
        }
    }

    suspend fun pushAllLocalBooks() = withContext(Dispatchers.IO + ExpectedSession(auth.requestSession())) {
        if (!auth.isSignedIn || !api.isConfigured) return@withContext
        val session = auth.requestSession()
        for (book in bookDao.getAll()) {
            auth.requireSession(session)
            try {
                pushProgress(ensureRemoteBook(book))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Other books may still sync; failed books remain available for the next retry.
            }
        }
    }

    suspend fun deleteRemoteBook(book: BookEntity) = withContext(Dispatchers.IO + ExpectedSession(auth.requestSession())) {
        if (!api.isConfigured || book.remoteOwnerId == null || book.remoteOwnerId != auth.accountId) return@withContext
        val remoteId = book.remoteBookId ?: return@withContext
        api.deleteBook(remoteId)
    }
}
