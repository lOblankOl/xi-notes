package com.xinotes.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY createdAt ASC")
    fun getAll(): Flow<List<NoteGroup>>

    @Insert
    suspend fun insert(group: NoteGroup): Long

    @Update
    suspend fun update(group: NoteGroup)

    @Delete
    suspend fun delete(group: NoteGroup)
}
