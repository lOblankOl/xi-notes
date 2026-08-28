package com.xinotes.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.xinotes.app.data.Note
import com.xinotes.app.data.NoteGroup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Список заметок с перетаскиванием: долгое нажатие + протяжка вверх/вниз меняет порядок.
 * Локальный список даёт мгновенный визуальный отклик во время перетаскивания;
 * итоговый порядок коммитится через onReorder только при отпускании пальца.
 *
 * ВАЖНО ДЛЯ ИСПОЛНИТЕЛЯ: ROW_HEIGHT_DP ниже подобран приблизительно (без реального
 * устройства под рукой). Если протяжка ощущается неточной — просто подогнать это
 * значение под фактическую высоту строки NoteRow после первого запуска на устройстве.
 */
private val ROW_HEIGHT_DP = 56

@Composable
fun ReorderableNoteList(
    notes: List<Note>,
    groups: List<NoteGroup>,
    onNoteClick: (Long) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onRenameNote: (Note, String) -> Unit,
    onMoveNoteToGroup: (Note, Long?) -> Unit,
    onReorder: (List<Note>) -> Unit
) {
    // БАГ, который был здесь: localOrder кэшировался по списку id заметок, поэтому
    // переименование (или любое другое изменение поля без изменения состава/порядка)
    // не долетало до отображения — панель показывала старые данные, пока не менялся
    // сам список id (например, при создании новой заметки). Теперь localOrder всегда
    // синхронизируется с актуальными notes, кроме момента активного перетаскивания
    // (чтобы drag не "прыгал" из-за одновременного обновления от Room).
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    var localOrder by remember { mutableStateOf(notes) }
    LaunchedEffect(notes) {
        if (draggingId == null) localOrder = notes
    }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { ROW_HEIGHT_DP.dp.toPx() }

    Column {
        localOrder.forEachIndexed { _, note ->
            val isDragging = draggingId == note.id
            NoteRow(
                note = note,
                groups = groups,
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .then(if (isDragging) Modifier.shadow(4.dp) else Modifier)
                    .let { mod ->
                        if (isDragging) {
                            mod.offset { IntOffset(0, dragOffsetPx.roundToInt()) }
                        } else mod
                    }
                    .pointerInput(note.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingId = note.id
                                dragOffsetPx = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetPx += dragAmount.y

                                val currentIndex = localOrder.indexOfFirst { it.id == note.id }
                                val targetIndex = (currentIndex + (dragOffsetPx / rowHeightPx).roundToInt())
                                    .coerceIn(0, localOrder.lastIndex)

                                if (targetIndex != currentIndex) {
                                    val mutable = localOrder.toMutableList()
                                    val item = mutable.removeAt(currentIndex)
                                    mutable.add(targetIndex, item)
                                    localOrder = mutable
                                    dragOffsetPx -= (targetIndex - currentIndex) * rowHeightPx
                                }
                            },
                            onDragEnd = {
                                draggingId = null
                                dragOffsetPx = 0f
                                onReorder(localOrder)
                            },
                            onDragCancel = {
                                draggingId = null
                                dragOffsetPx = 0f
                            }
                        )
                    },
                onClick = { onNoteClick(note.id) },
                onDelete = { onDeleteNote(note) },
                onRename = { newTitle -> onRenameNote(note, newTitle) },
                onMoveToGroup = { groupId -> onMoveNoteToGroup(note, groupId) }
            )
        }
    }
}

@Composable
private fun NoteRow(
    note: Note,
    groups: List<NoteGroup>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onMoveToGroup: (Long?) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT_DP.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(note.title.ifBlank { "Без названия" }, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                fmt.format(Date(note.updatedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Box {
            IconButton(onClick = { showMenu = true }) { Text("⋮") }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("Переименовать") }, onClick = {
                    showMenu = false; showRenameDialog = true
                })
                Text(
                    "Переместить в:",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.labelMedium
                )
                DropdownMenuItem(text = { Text("Без группы") }, onClick = {
                    showMenu = false; onMoveToGroup(null)
                })
                groups.forEach { g ->
                    DropdownMenuItem(text = { Text(g.name) }, onClick = {
                        showMenu = false; onMoveToGroup(g.id)
                    })
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Удалить заметку") }, onClick = {
                    showMenu = false; onDelete()
                })
            }
        }
    }

    if (showRenameDialog) {
        var text by remember { mutableStateOf(note.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Переименовать заметку") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }) },
            confirmButton = {
                TextButton(onClick = { onRename(text); showRenameDialog = false }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Отмена") } }
        )
    }
}
