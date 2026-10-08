package com.yishenghuang.heartext.data

import androidx.room.Entity
import kotlinx.coroutines.sync.Mutex

@Entity(tableName = "pending_book_deletions", primaryKeys = ["owner", "localId"])
data class PendingBookDeletion(val owner: String, val localId: String, val remoteId: String?)

/** Serializes registration, shelf replacement and deletion across repository instances. */
internal object BookMutations { val mutex = Mutex() }
