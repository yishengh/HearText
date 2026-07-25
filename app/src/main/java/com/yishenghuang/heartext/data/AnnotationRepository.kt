package com.yishenghuang.heartext.data

import com.yishenghuang.heartext.network.AuthTokenProvider
import com.yishenghuang.heartext.network.HearTextApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class AnnotationRepository(
    private val dao: AnnotationDao,
    private val api: HearTextApi,
    private val auth: AuthTokenProvider
) {
    fun observe(bookId: String): Flow<List<AnnotationEntity>> = dao.observeForBook(bookId)

    suspend fun syncFromServer(bookId: String, remoteBookId: String?) = withContext(Dispatchers.IO) {
        if (!auth.isSignedIn || !api.isConfigured || remoteBookId.isNullOrBlank()) return@withContext
        val remote = api.listAnnotations(remoteBookId)
        val entities = remote.map {
            AnnotationEntity(
                id = it.clientAnnotationId.ifBlank { it.id },
                bookId = bookId,
                clientAnnotationId = it.clientAnnotationId.ifBlank { it.id },
                type = it.type,
                chapterId = it.chapterId,
                chapterIndex = it.chapterIndex,
                startOffset = it.startOffset,
                endOffset = it.endOffset,
                selectedText = it.selectedText,
                color = it.color,
                note = it.note,
                remoteId = it.id,
                clientUpdatedAt = parseIso(it.clientUpdatedAt) ?: System.currentTimeMillis()
            )
        }
        dao.upsertAll(entities)
    }

    suspend fun addBookmark(
        bookId: String,
        remoteBookId: String?,
        chapterIndex: Int,
        pageIndex: Int = 0,
        chapterId: String? = null,
        note: String? = null
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
            note = note,
            clientUpdatedAt = now
        )
        dao.upsert(entity)
        pushIfPossible(entity, remoteBookId)
        entity
    }

    suspend fun findBookmark(
        bookId: String,
        chapterIndex: Int,
        pageIndex: Int
    ): AnnotationEntity? = withContext(Dispatchers.IO) {
        dao.listForBook(bookId).firstOrNull {
            it.type == "bookmark" &&
                it.chapterIndex == chapterIndex &&
                (it.startOffset ?: 0) == pageIndex.coerceAtLeast(0)
        }
    }

    /** Keep newest bookmark per chapter+page; delete older duplicates. */
    suspend fun dedupeBookmarks(bookId: String) = withContext(Dispatchers.IO) {
        val bookmarks = dao.listForBook(bookId).filter { it.type == "bookmark" }
        val keepIds = bookmarks
            .groupBy { (it.chapterIndex ?: 0) to (it.startOffset ?: 0) }
            .values
            .map { group -> group.maxBy { it.clientUpdatedAt }.id }
            .toSet()
        bookmarks.filter { it.id !in keepIds }.forEach { dao.delete(it.id) }
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
        dao.upsert(entity)
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
        dao.upsert(entity)
        pushIfPossible(entity, remoteBookId)
        entity
    }

    suspend fun delete(entity: AnnotationEntity) = withContext(Dispatchers.IO) {
        dao.delete(entity.id)
        val remoteId = entity.remoteId
        if (auth.isSignedIn && api.isConfigured && !remoteId.isNullOrBlank()) {
            runCatching { api.deleteAnnotation(remoteId) }
        }
    }

    private suspend fun pushIfPossible(entity: AnnotationEntity, remoteBookId: String?) {
        if (!auth.isSignedIn || !api.isConfigured || remoteBookId.isNullOrBlank()) return
        runCatching {
            val created = api.createAnnotation(
                clientAnnotationId = entity.clientAnnotationId,
                bookId = remoteBookId,
                type = entity.type,
                clientUpdatedAtIso = toIso(entity.clientUpdatedAt),
                chapterId = entity.chapterId,
                chapterIndex = entity.chapterIndex,
                startOffset = entity.startOffset,
                endOffset = entity.endOffset,
                selectedText = entity.selectedText,
                color = entity.color,
                note = entity.note
            )
            dao.upsert(entity.copy(remoteId = created.id))
        }
    }

    private fun toIso(epochMs: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(epochMs))
    }

    private fun parseIso(value: String): Long? = runCatching {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        fmt.parse(value.take(19))?.time
    }.getOrNull()
}
