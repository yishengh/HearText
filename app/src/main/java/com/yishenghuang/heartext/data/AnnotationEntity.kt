package com.yishenghuang.heartext.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "annotations")
data class AnnotationEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val clientAnnotationId: String,
    val type: String,
    val chapterId: String? = null,
    val chapterIndex: Int? = null,
    val startOffset: Int? = null,
    val endOffset: Int? = null,
    val selectedText: String? = null,
    val color: String? = null,
    val note: String? = null,
    val remoteId: String? = null,
    val clientUpdatedAt: Long = System.currentTimeMillis()
)

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE bookId = :bookId ORDER BY clientUpdatedAt DESC")
    fun observeForBook(bookId: String): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE bookId = :bookId")
    suspend fun listForBook(bookId: String): List<AnnotationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AnnotationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AnnotationEntity>)

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM annotations WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)
}
