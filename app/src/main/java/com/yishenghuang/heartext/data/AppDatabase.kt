package com.yishenghuang.heartext.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun fromFormat(value: BookFormat): String = value.name

    @TypeConverter
    fun toFormat(value: String): BookFormat =
        runCatching { BookFormat.valueOf(value) }.getOrDefault(BookFormat.TXT)

    @TypeConverter
    fun fromSource(value: BookSource): String = value.name

    @TypeConverter
    fun toSource(value: String): BookSource =
        runCatching { BookSource.valueOf(value) }.getOrDefault(BookSource.LOCAL)

    @TypeConverter
    fun fromCoverSource(value: CoverSource?): String? = value?.name

    @TypeConverter
    fun toCoverSource(value: String?): CoverSource? =
        value?.let { runCatching { CoverSource.valueOf(it) }.getOrNull() }
}

@Database(
    entities = [BookEntity::class, AnnotationEntity::class],
    version = 10,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun annotationDao(): AnnotationDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN remoteBookId TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN progressUpdatedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN locatorJson TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN catalogBookId TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN source TEXT NOT NULL DEFAULT 'LOCAL'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS annotations (
                        id TEXT NOT NULL PRIMARY KEY,
                        bookId TEXT NOT NULL,
                        clientAnnotationId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        chapterId TEXT,
                        chapterIndex INTEGER,
                        startOffset INTEGER,
                        endOffset INTEGER,
                        selectedText TEXT,
                        color TEXT,
                        note TEXT,
                        remoteId TEXT,
                        clientUpdatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS voice_profiles")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN coverUrl TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN coverSource TEXT")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN description TEXT")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN remoteOwnerId TEXT")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE annotations ADD COLUMN remoteOwnerId TEXT")
                db.execSQL("ALTER TABLE annotations ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE annotations ADD COLUMN deleteSynced INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE annotations ADD COLUMN locatorJson TEXT")
            }
        }

        internal val migrations = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
            MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "heartext.db"
                )
                    .addMigrations(*migrations)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
