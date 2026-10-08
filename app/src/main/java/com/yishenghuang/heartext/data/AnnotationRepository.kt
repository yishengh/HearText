package com.yishenghuang.heartext.data

import com.yishenghuang.heartext.network.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID

class AnnotationRepository(
    private val dao: AnnotationDao,
    private val api: HearTextApi,
    private val auth: SessionTokenProvider,
    private val books: BookDao
) {
    fun observe(bookId: String): Flow<List<AnnotationEntity>> = dao.observeForBook(bookId)

    private val syncLock = Mutex()

    suspend fun syncAll() {
        var incomplete = false
        for (book in books.getAll()) {
            try { syncFromServer(book.id, book.remoteBookId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { incomplete = true }
        }
        if (incomplete) throw SyncIncompleteException()
    }

    suspend fun syncFromServer(bookId: String, remoteBookId: String?) =
        withContext(Dispatchers.IO + ExpectedSession(auth.requestSession())) {
            val owner = auth.accountId ?: return@withContext
            if (!api.isConfigured) return@withContext
            val session = auth.requestSession()
            syncLock.withLock {
                auth.requireSession(session)
                val book = books.getBook(bookId) ?: return@withLock
                val remoteId = book.remoteBookId ?: return@withLock
                if (book.remoteOwnerId != owner || (remoteBookId != null && remoteBookId != remoteId)) return@withLock
                val before = dao.listIncludingDeleted(bookId)
                val remote = api.listAnnotations(remoteId)
                auth.requireSession(session)
                for (item in remote) {
                    if (item.bookId != remoteId) continue
                    val timestamp = runCatching { Instant.parse(item.clientUpdatedAt).toEpochMilli() }.getOrNull() ?: continue
                    dao.mergeRemote(AnnotationEntity(
                        id = UUID.randomUUID().toString(), bookId = bookId,
                        clientAnnotationId = item.clientAnnotationId.ifBlank { item.id }, type = item.type,
                        chapterId = item.chapterId, chapterIndex = item.chapterIndex,
                        locatorJson = item.locatorJson,
                        startOffset = item.startOffset, endOffset = item.endOffset,
                        selectedText = item.selectedText, color = item.color, note = item.note,
                        remoteId = item.id, clientUpdatedAt = timestamp, remoteOwnerId = owner
                    ))
                }
                // This endpoint returns the complete book list (no pagination).
                val present = remote.map { it.id }.toSet()
                for (local in before) {
                    val id = local.remoteId ?: continue
                    if (local.remoteOwnerId == owner && id !in present) {
                        dao.markRemoteAbsent(local.id, owner, id, local.clientUpdatedAt)
                    }
                }
                var incomplete = false
                for (local in dao.listIncludingDeleted(bookId)) {
                    auth.requireSession(session)
                    if (local.remoteOwnerId != null && local.remoteOwnerId != owner) continue
                    try {
                        if (local.deleted) deleteRemote(local, owner)
                        else if (local.remoteId == null) push(local, remoteId, owner)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { incomplete = true }
                }
                if (incomplete) throw SyncIncompleteException()
            }
        }

    suspend fun addBookmark(
        bookId: String,
        remoteBookId: String?,
        chapterIndex: Int,
        pageIndex: Int = 0,
        chapterId: String? = null,
        note: String? = null,
        characterOffset: Int? = null
    ): AnnotationEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val clientId = UUID.randomUUID().toString()
        val entity = AnnotationEntity(
            id = clientId,
            bookId = bookId,
            clientAnnotationId = clientId,
            type = "bookmark",
            chapterId = chapterId,
            chapterIndex = chapterIndex,
            // Reuse startOffset as 0-based page index for bookmarks.
            startOffset = pageIndex.coerceAtLeast(0),
            locatorJson = characterOffset?.takeIf { it >= 0 }?.let { TextPosition(chapterIndex, it).encode() },
            note = note,
            clientUpdatedAt = now
        )
        dao.insertLocal(entity)
        pushIfPossible(entity, remoteBookId)
        entity
    }

    suspend fun findBookmark(
        bookId: String,
        chapterIndex: Int,
        pageIndex: Int,
        characterRange: IntRange? = null
    ): AnnotationEntity? = withContext(Dispatchers.IO) {
        dao.listForBook(bookId).firstOrNull {
            it.type == "bookmark" &&
                it.chapterIndex == chapterIndex &&
                it.matchesBookmarkPage(chapterIndex, pageIndex, characterRange)
        }
    }

    /** Keep newest bookmark per chapter+page; delete older duplicates. */
    suspend fun dedupeBookmarks(bookId: String) = withContext(Dispatchers.IO) {
        val bookmarks = dao.listForBook(bookId).filter { it.type == "bookmark" }
        val keepIds = bookmarks
            .groupBy { it.bookmarkPositionKey() }
            .values
            .map { group -> group.maxBy { it.clientUpdatedAt }.id }
            .toSet()
        bookmarks.filter { it.id !in keepIds }.forEach { delete(it) }
    }

    suspend fun addHighlight(
        bookId: String,
        remoteBookId: String?,
        chapterIndex: Int,
        selectedText: String,
        startOffset: Int?,
        endOffset: Int?,
        color: String = "#FFEB3B",
        note: String? = null
    ): AnnotationEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val clientId = UUID.randomUUID().toString()
        val entity = AnnotationEntity(
            id = clientId,
            bookId = bookId,
            clientAnnotationId = clientId,
            type = "highlight",
            chapterIndex = chapterIndex,
            startOffset = startOffset,
            endOffset = endOffset,
            selectedText = selectedText,
            color = color,
            note = note,
            clientUpdatedAt = now
        )
        dao.insertLocal(entity)
        pushIfPossible(entity, remoteBookId)
        entity
    }

    suspend fun addNote(
        bookId: String,
        remoteBookId: String?,
        chapterIndex: Int,
        note: String,
        selectedText: String? = null
    ): AnnotationEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val clientId = UUID.randomUUID().toString()
        val entity = AnnotationEntity(
            id = clientId,
            bookId = bookId,
            clientAnnotationId = clientId,
            type = "note",
            chapterIndex = chapterIndex,
            selectedText = selectedText,
            note = note,
            clientUpdatedAt = now
        )
        dao.insertLocal(entity)
        pushIfPossible(entity, remoteBookId)
        entity
    }

    suspend fun delete(entity: AnnotationEntity) = withContext(Dispatchers.IO) {
        dao.markDeleted(entity.id, System.currentTimeMillis())
        val book = books.getBook(entity.bookId)
        pushIfPossible(entity, book?.remoteBookId)
    }

    private suspend fun pushIfPossible(entity: AnnotationEntity, remoteBookId: String?) =
        withContext(Dispatchers.IO + ExpectedSession(auth.requestSession())) {
            val owner = auth.accountId ?: return@withContext
            if (!api.isConfigured || remoteBookId == null) return@withContext
            val session = auth.requestSession()
            syncLock.withLock {
                auth.requireSession(session)
                val book = books.getBook(entity.bookId) ?: return@withLock
                if (book.remoteOwnerId != owner || book.remoteBookId != remoteBookId) return@withLock
                val current = dao.get(entity.id) ?: return@withLock
                if (current.remoteOwnerId != null && current.remoteOwnerId != owner) return@withLock
                try {
                    if (current.deleted) deleteRemote(current, owner)
                    else if (current.remoteId == null) push(current, remoteBookId, owner)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Local edit succeeded; sync will retry. */ }
            }
        }

    private suspend fun push(entity: AnnotationEntity, remoteBookId: String, owner: String) {
        val created = api.createAnnotation(
            clientAnnotationId = entity.clientAnnotationId, bookId = remoteBookId, type = entity.type,
            clientUpdatedAtIso = Instant.ofEpochMilli(entity.clientUpdatedAt).toString(),
            chapterId = entity.chapterId, chapterIndex = entity.chapterIndex,
            startOffset = entity.startOffset, endOffset = entity.endOffset,
            selectedText = entity.selectedText, color = entity.color, note = entity.note,
            locatorJson = entity.locatorJson
        )
        check(created.bookId == remoteBookId && created.clientAnnotationId == entity.clientAnnotationId)
        dao.bindRemote(entity.id, created.id, owner)
        // A user can delete while POST is in flight. Bind only, never restore its old content.
        dao.get(entity.id)?.takeIf { it.deleted }?.let { deleteRemote(it, owner) }
    }

    private suspend fun deleteRemote(entity: AnnotationEntity, owner: String) {
        if (!entity.deleted || entity.deleteSynced || entity.remoteOwnerId != owner) return
        val remoteId = entity.remoteId ?: return
        try { api.deleteAnnotation(remoteId) }
        catch (failure: ApiHttpException) { if (failure.code != 404) throw failure }
        dao.acknowledgeDelete(entity.id, remoteId)
    }
}
