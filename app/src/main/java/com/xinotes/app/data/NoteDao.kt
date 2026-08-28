package com.xinotes.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY sortOrder ASC")
    fun getAll(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE reminderAt IS NOT NULL ORDER BY reminderAt ASC")
    fun getWithReminders(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): Note?

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)

    // При удалении группы её заметки не удаляются — переходят в "Без группы".
    @Query("UPDATE notes SET groupId = NULL WHERE groupId = :groupId")
    suspend fun ungroupAll(groupId: Long)
}
