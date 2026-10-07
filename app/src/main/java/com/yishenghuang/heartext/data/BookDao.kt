package com.yishenghuang.heartext.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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

    @Update
    suspend fun update(book: BookEntity)

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
