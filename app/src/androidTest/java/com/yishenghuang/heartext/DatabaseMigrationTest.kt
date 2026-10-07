package com.yishenghuang.heartext

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.yishenghuang.heartext.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DatabaseMigrationTest {
    @Test fun oldestAndPreviousSchemasPreserveBooksAndAnnotations() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(BuildConfig.APPLICATION_ID.endsWith(".validation"))
        for (version in listOf(1, 7, 8)) {
            val name = "migration-${UUID.randomUUID()}.db"
            val helper = FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                    .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL("""CREATE TABLE books (id TEXT NOT NULL PRIMARY KEY,
                                title TEXT NOT NULL, author TEXT NOT NULL, format TEXT NOT NULL,
                                filePath TEXT NOT NULL, coverPath TEXT, lastChapterIndex INTEGER NOT NULL,
                                lastOffset INTEGER NOT NULL, progressPercent REAL NOT NULL,
                                totalChapters INTEGER NOT NULL, addedAt INTEGER NOT NULL)""")
                            AppDatabase.migrations.filter { it.endVersion <= version }.forEach { it.migrate(db) }
                            db.execSQL("""INSERT INTO books (id,title,author,format,filePath,coverPath,
                                lastChapterIndex,lastOffset,progressPercent,totalChapters,addedAt)
                                VALUES ('kept','Title','Author','TXT','/local/book.txt',NULL,2,37,42.5,5,1234)""")
                            if (version >= 7) {
                                db.execSQL("UPDATE books SET remoteBookId='remote', progressUpdatedAt=5678, locatorJson='saved'")
                                db.execSQL("""INSERT INTO annotations (id,bookId,clientAnnotationId,type,clientUpdatedAt)
                                    VALUES ('note','kept','note','note',4321)""")
                            }
                        }
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected")
                    }).build())
            try { helper.writableDatabase } finally { helper.close() }
            val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(*AppDatabase.migrations).build()
            try {
                val book = requireNotNull(database.bookDao().getBook("kept"))
                assertEquals(37, book.lastOffset)
                assertEquals(42.5f, book.progressPercent)
                assertEquals("/local/book.txt", book.filePath)
                assertNull(book.remoteOwnerId)
                if (version >= 7) {
                    assertEquals("remote", book.remoteBookId)
                    assertEquals("saved", book.locatorJson)
                    assertEquals(5678L, book.progressUpdatedAt)
                    assertFalse(database.annotationDao().get("note")!!.deleted)
                    database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM annotations").use {
                        assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0))
                    }
                }
            } finally { database.close(); context.deleteDatabase(name) }
        }
    }
}
