package com.xinotes.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xinotes.app.data.AppDatabase
import com.xinotes.app.data.Note
import com.xinotes.app.data.NoteGroup
import com.xinotes.app.data.NoteRepository
import com.xinotes.app.reminder.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val notes: List<Note> = emptyList(),
    val reminders: List<Note> = emptyList(),
    val groups: List<NoteGroup> = emptyList(),
    val selectedNoteId: Long? = null
)

class NotesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = NoteRepository(app, AppDatabase.getInstance(app))
    private val selectedNoteId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<UiState> = combine(
        repo.allNotes(), repo.notesWithReminders(), repo.allGroups(), selectedNoteId
    ) { notes, reminders, groups, selected ->
        UiState(notes, reminders, groups, selected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    fun selectNote(id: Long?) {
        selectedNoteId.value = id
    }

    fun newNote(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repo.saveNote(Note(title = "Новая заметка"))
            onCreated(id)
        }
    }

    fun saveNote(note: Note) {
        viewModelScope.launch { repo.saveNote(note.copy(updatedAt = System.currentTimeMillis())) }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            ReminderScheduler.cancel(getApplication(), note.id)
            repo.deleteNote(note)
            if (selectedNoteId.value == note.id) selectedNoteId.value = null
        }
    }

    fun renameNote(note: Note, newTitle: String) {
        viewModelScope.launch {
            repo.saveNote(note.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
        }
    }

    fun setReminder(note: Note, triggerAtMillis: Long) {
        viewModelScope.launch {
            repo.saveNote(note.copy(reminderAt = triggerAtMillis))
            ReminderScheduler.schedule(
                getApplication(), note.id,
                note.title.ifBlank { "Заметка" },
                note.content.take(80),
                triggerAtMillis
            )
        }
    }

    fun clearReminder(note: Note) {
        viewModelScope.launch {
            repo.saveNote(note.copy(reminderAt = null))
            ReminderScheduler.cancel(getApplication(), note.id)
        }
    }

    fun createGroup(name: String) {
        viewModelScope.launch { repo.createGroup(name) }
    }

    fun deleteGroup(group: NoteGroup) {
        viewModelScope.launch { repo.deleteGroup(group) }
    }

    fun renameGroup(group: NoteGroup, name: String) {
        viewModelScope.launch { repo.renameGroup(group, name) }
    }

    fun moveNoteToGroup(note: Note, groupId: Long?) {
        viewModelScope.launch { repo.saveNote(note.copy(groupId = groupId)) }
    }

    fun reorderNotes(reordered: List<Note>) {
        viewModelScope.launch {
            reordered.forEachIndexed { index, note ->
                repo.saveNote(note.copy(sortOrder = index))
            }
        }
    }
}
