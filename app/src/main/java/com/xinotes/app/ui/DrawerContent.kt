package com.xinotes.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xinotes.app.data.Note
import com.xinotes.app.data.NoteGroup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DrawerContent(
    uiState: UiState,
    onNoteSelected: (Long) -> Unit,
    onNewNote: () -> Unit,
    onDeleteNote: (Note) -> Unit,
    onRenameNote: (Note, String) -> Unit,
    onCreateGroup: (String) -> Unit,
    onDeleteGroup: (NoteGroup) -> Unit,
    onRenameGroup: (NoteGroup, String) -> Unit,
    onMoveNoteToGroup: (Note, Long?) -> Unit,
    onReorderNotes: (List<Note>) -> Unit
) {
    var showNewGroupDialog by remember { mutableStateOf(false) }
    var expandedGroups by remember { mutableStateOf(setOf<Long>()) }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Ξ-notes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = onNewNote) { Icon(Icons.Filled.Add, contentDescription = "Новая заметка") }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {

            // --- Секция "Напоминания" (заметки целиком + отдельные пункты чек-листов) ---
            if (uiState.reminders.isNotEmpty()) {
                item { SectionHeader("Напоминания") }
                items(uiState.reminders, key = { "rem-${it.noteId}-${it.itemId}" }) { entry ->
                    ReminderRow(entry = entry, onClick = { onNoteSelected(entry.noteId) })
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
            }

            // --- Секция "Заметки" ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader("Заметки")
                    TextButton(onClick = { showNewGroupDialog = true }) { Text("+ группа") }
                }
            }

            uiState.groups.forEach { group ->
                val expanded = expandedGroups.contains(group.id)
                val groupNotes = uiState.notes.filter { it.groupId == group.id }
                item(key = "group-${group.id}") {
                    GroupHeader(
                        group = group,
                        expanded = expanded,
                        onToggle = {
                            expandedGroups = if (expanded) expandedGroups - group.id else expandedGroups + group.id
                        },
                        onDelete = { onDeleteGroup(group) },
                        onRename = { newName -> onRenameGroup(group, newName) }
                    )
                }
                if (expanded) {
                    item(key = "reorder-${group.id}") {
                        ReorderableNoteList(
                            notes = groupNotes,
                            groups = uiState.groups,
                            onNoteClick = onNoteSelected,
                            onDeleteNote = onDeleteNote,
                            onRenameNote = onRenameNote,
                            onMoveNoteToGroup = onMoveNoteToGroup,
                            onReorder = onReorderNotes
                        )
                    }
                }
            }

            item(key = "ungrouped-header") { SectionHeader("Без группы") }
            item(key = "ungrouped-list") {
                ReorderableNoteList(
                    notes = uiState.notes.filter { it.groupId == null },
                    groups = uiState.groups,
                    onNoteClick = onNoteSelected,
                    onDeleteNote = onDeleteNote,
                    onRenameNote = onRenameNote,
                    onMoveNoteToGroup = onMoveNoteToGroup,
                    onReorder = onReorderNotes
                )
            }
        }
    }

    if (showNewGroupDialog) {
        NewGroupDialog(
            onConfirm = { name -> onCreateGroup(name); showNewGroupDialog = false },
            onDismiss = { showNewGroupDialog = false }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun ReminderRow(entry: ReminderEntry, onClick: () -> Unit) {
    val fmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Alarm, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(entry.displayTitle, style = MaterialTheme.typography.bodyMedium)
            Row {
                Text(
                    fmt.format(Date(entry.timeMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                // Помечаем, что это напоминание конкретного пункта списка, а не всей заметки.
                if (entry.itemId != null) {
                    Text(
                        " · пункт списка",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(
    group: NoteGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(group.name, fontWeight = FontWeight.SemiBold)
        }
        Box {
            IconButton(onClick = { showMenu = true }) { Text("⋮") }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("Переименовать") }, onClick = {
                    showMenu = false; showRenameDialog = true
                })
                DropdownMenuItem(text = { Text("Удалить группу") }, onClick = {
                    showMenu = false; onDelete()
                })
            }
        }
    }

    if (showRenameDialog) {
        var text by remember { mutableStateOf(group.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Переименовать группу") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }) },
            confirmButton = {
                TextButton(onClick = { onRename(text); showRenameDialog = false }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun NewGroupDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая группа") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, placeholder = { Text("Название") }) },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
