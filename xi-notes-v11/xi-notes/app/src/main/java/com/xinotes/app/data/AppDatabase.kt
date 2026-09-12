package com.xinotes.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * ВАЖНО: MIGRATION_1_2 добавляет новые колонки через ALTER TABLE — это БЕЗОПАСНО,
 * существующие строки (заметки, напоминания) не удаляются и не пересоздаются.
 * Ни в коем случае не заменять это на fallbackToDestructiveMigration() — это стирает
 * всю базу целиком при любом несовпадении схемы.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN repeatMode TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE notes ADD COLUMN color TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE groups ADD COLUMN color TEXT DEFAULT NULL")
    }
}

@Database(entities = [Note::class, NoteGroup::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun groupDao(): GroupDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "xi-notes.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
