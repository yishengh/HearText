package com.yishenghuang.heartext.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val format: BookFormat,
    val filePath: String,
    val coverPath: String? = null,
    /** Remote cover URL from backend / catalog when available. */
    val coverUrl: String? = null,
    /** How [coverPath] was obtained; USER covers are never overwritten by sync. */
    val coverSource: CoverSource? = null,
    val lastChapterIndex: Int = 0,
    val lastOffset: Int = 0,
    val progressPercent: Float = 0f,
    val totalChapters: Int = 1,
    val addedAt: Long = System.currentTimeMillis(),
    /** Server UUID from POST /v1/books; null until first successful sync. */
    val remoteBookId: String? = null,
    /** Verified account or durable registration owner; null before registration/for legacy books. */
    val remoteOwnerId: String? = null,
    /** Epoch millis used for progress LWW as client_updated_at. */
    val progressUpdatedAt: Long = 0L,
    /** Serialized Readium Locator JSON for industrial reader resume. */
    val locatorJson: String? = null,
    /** Catalog book id when sourced from bookstore. */
    val catalogBookId: String? = null,
    /** Book synopsis from catalog / metadata when available. */
    val description: String? = null,
    val source: BookSource = BookSource.LOCAL
)
