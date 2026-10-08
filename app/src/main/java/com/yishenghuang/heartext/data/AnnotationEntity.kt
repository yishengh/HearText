package com.yishenghuang.heartext.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.ColumnInfo
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
    val clientUpdatedAt: Long = System.currentTimeMillis(),
    val remoteOwnerId: String? = null,
    val locatorJson: String? = null,
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "0") val deleteSynced: Boolean = false
)

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE bookId = :bookId AND deleted = 0 ORDER BY clientUpdatedAt DESC")
    fun observeForBook(bookId: String): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE bookId = :bookId AND deleted = 0")
    suspend fun listForBook(bookId: String): List<AnnotationEntity>

    @Query("SELECT * FROM annotations WHERE bookId = :bookId")
    suspend fun listIncludingDeleted(bookId: String): List<AnnotationEntity>

    @Query("SELECT * FROM annotations WHERE id = :id")
    suspend fun get(id: String): AnnotationEntity?

    @Query("SELECT * FROM annotations WHERE bookId = :bookId AND clientAnnotationId = :clientId LIMIT 1")
    suspend fun getByClient(bookId: String, clientId: String): AnnotationEntity?

    @Query("SELECT COUNT(*) FROM books WHERE id = :bookId AND remoteOwnerId = :owner")
    suspend fun ownsBook(bookId: String, owner: String): Int

    @Query("SELECT COUNT(*) FROM books WHERE id = :bookId")
    suspend fun bookExists(bookId: String): Int

    @Transaction
    suspend fun insertLocal(entity: AnnotationEntity) {
        if (bookExists(entity.bookId) == 0) throw kotlinx.coroutines.CancellationException("Book removed")
        upsert(entity)
    }

    @Transaction
    suspend fun mergeRemote(incoming: AnnotationEntity) {
        val owner = incoming.remoteOwnerId ?: return
        if (ownsBook(incoming.bookId, owner) == 0) return
        val existing = getByClient(incoming.bookId, incoming.clientAnnotationId)
        if (existing?.remoteOwnerId != null && existing.remoteOwnerId != owner) return
        val merged = when {
            existing == null -> incoming
            existing.deleted -> existing.copy(remoteId = incoming.remoteId, remoteOwnerId = owner, deleteSynced = false)
            incoming.clientUpdatedAt > existing.clientUpdatedAt -> incoming.copy(id = existing.id)
            else -> existing.copy(remoteId = incoming.remoteId, remoteOwnerId = owner)
        }
        upsert(merged)
    }

    @Query("""UPDATE annotations SET remoteId = :remoteId, remoteOwnerId = :owner
        WHERE id = :id AND (remoteOwnerId IS NULL OR remoteOwnerId = :owner)
        AND EXISTS (SELECT 1 FROM books WHERE books.id = annotations.bookId AND books.remoteOwnerId = :owner)""")
    suspend fun bindRemote(id: String, remoteId: String, owner: String)

    @Query("""UPDATE annotations SET deleted = 1, deleteSynced = 0, note = NULL, selectedText = NULL,
        clientUpdatedAt = MAX(clientUpdatedAt + 1, :now) WHERE id = :id""")
    suspend fun markDeleted(id: String, now: Long)

    @Query("UPDATE annotations SET deleteSynced = 1 WHERE id = :id AND remoteId = :remoteId AND deleted = 1")
    suspend fun acknowledgeDelete(id: String, remoteId: String)

    @Query("""UPDATE annotations SET deleted = 1, deleteSynced = 1, note = NULL, selectedText = NULL WHERE id = :id
        AND remoteOwnerId = :owner AND remoteId = :remoteId AND clientUpdatedAt = :timestamp""")
    suspend fun markRemoteAbsent(id: String, owner: String, remoteId: String, timestamp: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AnnotationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AnnotationEntity>)

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM annotations WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)
}
