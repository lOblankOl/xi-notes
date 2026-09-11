package com.xinotes.app.data

import android.content.Context
import com.xinotes.app.util.ImageStore
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val context: Context, db: AppDatabase) {
    private val noteDao = db.noteDao()
    private val groupDao = db.groupDao()

    fun allNotes(): Flow<List<Note>> = noteDao.getAll()
    fun allGroups(): Flow<List<NoteGroup>> = groupDao.getAll()

    suspend fun getNote(id: Long): Note? = noteDao.getById(id)

    suspend fun saveNote(note: Note): Long {
        return if (note.id == 0L) {
            noteDao.insert(note)
        } else {
            noteDao.update(note)
            note.id
        }
    }

    suspend fun deleteNote(note: Note) {
        ImageStore.deleteImagesInContent(context, note.content)
        noteDao.delete(note)
    }

    suspend fun createGroup(name: String): Long = groupDao.insert(NoteGroup(name = name))

    suspend fun deleteGroup(group: NoteGroup) {
        noteDao.ungroupAll(group.id)
        groupDao.delete(group)
    }

    suspend fun renameGroup(group: NoteGroup, newName: String) {
        groupDao.update(group.copy(name = newName))
    }
}
