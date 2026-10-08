package com.yishenghuang.heartext.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    fun observeBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeBook(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBook(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE remoteBookId = :remoteId LIMIT 1")
    suspend fun getBookByRemoteId(remoteId: String): BookEntity?

    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    suspend fun getAll(): List<BookEntity>

    @Query("SELECT * FROM books ORDER BY addedAt DESC LIMIT 1")
    suspend fun getMostRecent(): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: BookEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(book: BookEntity): Long

    /** Resolve account collisions and preserve user fields in the same transaction as replacement. */
    @Transaction
    suspend fun saveCatalogDownload(download: BookEntity): BookEntity {
        val byRemote = download.remoteBookId?.let { getBookByRemoteId(it) }
        val byId = getBook(download.id)
        fun compatible(book: BookEntity?): Boolean = book != null &&
            book.source == BookSource.CATALOG && book.catalogBookId == download.catalogBookId &&
            (book.remoteOwnerId == null || book.remoteOwnerId == download.remoteOwnerId) &&
            (book.remoteBookId == null || book.remoteBookId == download.remoteBookId)
        val existing = byRemote?.takeIf { compatible(it) } ?: byId?.takeIf { compatible(it) }
        val saved = if (existing != null) existing.copy(
            filePath = download.filePath, remoteBookId = download.remoteBookId,
            remoteOwnerId = download.remoteOwnerId, totalChapters = download.totalChapters,
            coverUrl = download.coverUrl ?: existing.coverUrl,
            coverPath = existing.coverPath ?: download.coverPath,
            coverSource = existing.coverSource ?: download.coverSource,
            description = existing.description ?: download.description
        ) else download.copy(id = if (byId == null) download.id else java.util.UUID.randomUUID().toString())
        upsert(saved)
        return saved
    }

    @Query("SELECT COUNT(*) FROM books WHERE filePath = :path")
    suspend fun countFileReferences(path: String): Int

    @Update
    suspend fun update(book: BookEntity)

    @Query("""UPDATE books SET remoteBookId = :remoteId, remoteOwnerId = :owner,
        coverUrl = COALESCE(:url, coverUrl) WHERE id = :id
        AND (remoteOwnerId IS NULL OR remoteOwnerId = :owner)
        AND (remoteBookId IS NULL OR remoteBookId = :remoteId)""")
    suspend fun bindRemote(id: String, remoteId: String, owner: String, url: String?): Int

    @Query("""UPDATE books SET coverPath = :path, coverSource = :source
        WHERE id = :id AND remoteOwnerId = :owner AND coverPath IS :expectedPath
        AND (coverSource IS NULL OR coverSource != 'USER')""")
    suspend fun updateRemoteCover(id: String, owner: String, expectedPath: String?, path: String,
        source: CoverSource): Int

    @Query("""UPDATE books SET lastChapterIndex = :chapter, lastOffset = :offset,
        progressPercent = :percent, progressUpdatedAt = :updatedAt, locatorJson = NULL
        WHERE id = :id AND remoteOwnerId = :owner AND progressUpdatedAt < :updatedAt""")
    suspend fun mergeRemoteProgress(id: String, owner: String, chapter: Int, offset: Int,
        percent: Float, updatedAt: Long)

    @Query("UPDATE books SET coverPath = :path, coverSource = :source WHERE id = :id")
    suspend fun updateCover(id: String, path: String?, source: CoverSource?)

    @Query("UPDATE books SET description = :description WHERE id = :id")
    suspend fun updateDescription(id: String, description: String)

    @Query("UPDATE books SET totalChapters = :count WHERE id = :id")
    suspend fun updateChapterCount(id: String, count: Int)

    @Query("""UPDATE books SET lastChapterIndex = :chapter, lastOffset = :offset,
        progressPercent = :percent, progressUpdatedAt = :updatedAt,
        locatorJson = COALESCE(:locator, locatorJson), totalChapters = COALESCE(:chapterCount, totalChapters)
        WHERE id = :id AND progressUpdatedAt <= :updatedAt""")
    suspend fun updateProgress(id: String, chapter: Int, offset: Int, percent: Float,
        updatedAt: Long, locator: String?, chapterCount: Int?)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM books")
    suspend fun count(): Int
}
