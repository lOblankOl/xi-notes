package com.xinotes.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xinotes.app.data.AppDatabase
import com.xinotes.app.data.Note
import com.xinotes.app.data.NoteGroup
import com.xinotes.app.data.NoteRepository
import com.xinotes.app.reminder.ReminderScheduler
import com.xinotes.app.util.ChecklistParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Один пункт в секции "Напоминания" — либо заметка целиком (itemId == null), либо
 *  отдельный пункт чек-листа внутри неё (itemId — его id внутри текста заметки). */
data class ReminderEntry(
    val noteId: Long,
    val itemId: Int?,
    val displayTitle: String,
    val timeMillis: Long,
    val repeatMode: String?
)

data class UiState(
    val notes: List<Note> = emptyList(),
    val reminders: List<ReminderEntry> = emptyList(),
    val groups: List<NoteGroup> = emptyList(),
    val selectedNoteId: Long? = null
)

private fun buildReminderEntries(notes: List<Note>): List<ReminderEntry> {
    val entries = mutableListOf<ReminderEntry>()
    notes.forEach { note ->
        if (note.reminderAt != null) {
            entries.add(
                ReminderEntry(
                    note.id, null, note.title.ifBlank { "Без названия" },
                    note.reminderAt, note.repeatMode
                )
            )
        }
        ChecklistParser.extractItemReminders(note.content).forEach { item ->
            entries.add(
                ReminderEntry(
                    note.id, item.itemId, item.text.ifBlank { "Пункт списка" },
                    item.reminderAt, item.repeatMode
                )
            )
        }
    }
    return entries.sortedBy { it.timeMillis }
}

class NotesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = NoteRepository(app, AppDatabase.getInstance(app))

    // Один-единственный ключ в SharedPreferences — какая заметка была открыта последней.
    // Просто перезаписывается при каждом переключении, ничего не накапливается.
    private val prefs = app.getSharedPreferences("xi_notes_prefs", Context.MODE_PRIVATE)
    private val selectedNoteId = MutableStateFlow(
        prefs.getLong(KEY_LAST_NOTE_ID, -1L).takeIf { it != -1L }
    )

    val uiState: StateFlow<UiState> = combine(
        repo.allNotes(), repo.allGroups(), selectedNoteId
    ) { notes, groups, selected ->
        UiState(notes, buildReminderEntries(notes), groups, selected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    fun selectNote(id: Long?) {
        selectedNoteId.value = id
        prefs.edit().putLong(KEY_LAST_NOTE_ID, id ?: -1L).apply()
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
            ReminderScheduler.cancel(getApplication(), note.id.toInt())
            repo.deleteNote(note)
            if (selectedNoteId.value == note.id) selectNote(null)
        }
    }

    fun renameNote(note: Note, newTitle: String) {
        viewModelScope.launch {
            repo.saveNote(note.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
        }
    }

    fun setReminder(note: Note, triggerAtMillis: Long, repeatMode: String?) {
        viewModelScope.launch {
            repo.saveNote(note.copy(reminderAt = triggerAtMillis, repeatMode = repeatMode))
            ReminderScheduler.schedule(
                getApplication(), note.id.toInt(), note.id,
                note.title.ifBlank { "Заметка" },
                note.content.take(80),
                triggerAtMillis,
                repeatMode
            )
        }
    }

    fun clearReminder(note: Note) {
        viewModelScope.launch {
            repo.saveNote(note.copy(reminderAt = null, repeatMode = null))
            ReminderScheduler.cancel(getApplication(), note.id.toInt())
        }
    }

    fun setNoteColor(note: Note, color: String?) {
        viewModelScope.launch { repo.saveNote(note.copy(color = color)) }
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

    fun setGroupColor(group: NoteGroup, color: String?) {
        viewModelScope.launch { repo.setGroupColor(group, color) }
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

    companion object {
        private const val KEY_LAST_NOTE_ID = "last_note_id"
    }
}
