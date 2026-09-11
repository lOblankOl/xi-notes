package com.xinotes.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.xinotes.app.data.Note
import com.xinotes.app.util.ImageStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

private data class ContentBlock(val isImage: Boolean, val text: String = "", val imageFile: String = "")

private fun parseContent(content: String): List<ContentBlock> {
    val regex = Regex("""\[\[img:([^]]+)]]""")
    val blocks = mutableListOf<ContentBlock>()
    var lastIndex = 0
    regex.findAll(content).forEach { match ->
        if (match.range.first > lastIndex) {
            blocks.add(ContentBlock(isImage = false, text = content.substring(lastIndex, match.range.first)))
        }
        blocks.add(ContentBlock(isImage = true, imageFile = match.groupValues[1]))
        lastIndex = match.range.last + 1
    }
    if (lastIndex < content.length) {
        blocks.add(ContentBlock(isImage = false, text = content.substring(lastIndex)))
    }
    if (blocks.isEmpty()) blocks.add(ContentBlock(isImage = false, text = ""))
    return blocks
}

private fun serializeContent(blocks: List<ContentBlock>): String =
    blocks.joinToString("") { if (it.isImage) "[[img:${it.imageFile}]]" else it.text }

private fun commitNow(blocks: List<ContentBlock>, note: Note, onSave: (Note) -> Unit) {
    val content = serializeContent(blocks)
    val title = content.lineSequence().firstOrNull { it.isNotBlank() }?.take(60) ?: ""
    onSave(note.copy(content = content, title = title))
}

/**
 * Экран редактирования одной заметки.
 * Текст и картинки хранятся как последовательность "блоков" — это то, что в ТЗ
 * называлось разметкой [[img:filename]] внутри content: порядок текст/картинка,
 * который расставил пользователь, сохраняется как есть.
 *
 * Автосохранение текста — с debounce ~1 секунда после паузы в наборе (см. saveJob),
 * чтобы не писать на диск при каждом нажатии клавиши. Вставка/удаление картинки —
 * это структурное изменение, сохраняется сразу.
 */
@Composable
fun NoteEditorScreen(
    note: Note,
    onSave: (Note) -> Unit,
    onSetReminder: (Note, Long) -> Unit,
    onClearReminder: (Note) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saveJob by remember(note.id) { mutableStateOf<Job?>(null) }

    var blocks by remember(note.id) { mutableStateOf(parseContent(note.content)) }
    var focusedBlockIndex by remember(note.id) { mutableStateOf(0) }

    fun commitDebounced() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1000)
            commitNow(blocks, note, onSave)
        }
    }

    fun commitImmediate() {
        saveJob?.cancel()
        commitNow(blocks, note, onSave)
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val filename = ImageStore.importImage(context, uri)
            if (filename != null) {
                val mutable = blocks.toMutableList()
                val insertAt = (focusedBlockIndex + 1).coerceIn(0, mutable.size)
                mutable.add(insertAt, ContentBlock(isImage = true, imageFile = filename))
                mutable.add(insertAt + 1, ContentBlock(isImage = false, text = ""))
                blocks = mutable
                commitImmediate()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = {
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Icon(Icons.Filled.Image, contentDescription = "Вставить картинку")
            }

            IconButton(onClick = {
                val cal = Calendar.getInstance()
                // Сначала выбор даты (календарь), потом времени — напоминание может
                // стоять на любой день вперёд, не только на сегодня.
                DatePickerDialog(
                    context,
                    { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
                        cal.set(Calendar.YEAR, year)
                        cal.set(Calendar.MONTH, month)
                        cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        TimePickerDialog(
                            context,
                            { _: TimePicker, hour: Int, minute: Int ->
                                cal.set(Calendar.HOUR_OF_DAY, hour)
                                cal.set(Calendar.MINUTE, minute)
                                cal.set(Calendar.SECOND, 0)
                                onSetReminder(note, cal.timeInMillis)
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            true
                        ).show()
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }) {
                Icon(
                    if (note.reminderAt != null) Icons.Filled.Alarm else Icons.Filled.AlarmOff,
                    contentDescription = "Напоминание"
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            blocks.forEachIndexed { index, block ->
                if (block.isImage) {
                    Box(modifier = Modifier.padding(vertical = 6.dp)) {
                        AsyncImage(
                            model = ImageStore.fileFor(context, block.imageFile),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(
                            onClick = {
                                val mutable = blocks.toMutableList()
                                mutable.removeAt(index)
                                blocks = mutable
                                commitImmediate()
                            },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) { Text("✕") }
                    }
                } else {
                    OutlinedTextField(
                        value = block.text,
                        onValueChange = { newText ->
                            val mutable = blocks.toMutableList()
                            mutable[index] = block.copy(text = newText)
                            blocks = mutable
                            focusedBlockIndex = index
                            commitDebounced()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (it.isFocused) focusedBlockIndex = index },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent
                        ),
                        placeholder = { if (index == 0) Text("Текст заметки...") }
                    )
                }
            }
        }
    }
}
