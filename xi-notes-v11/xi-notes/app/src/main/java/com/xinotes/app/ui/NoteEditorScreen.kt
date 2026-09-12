package com.xinotes.app.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.widget.DatePicker
import android.widget.TimePicker
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.xinotes.app.data.Note
import com.xinotes.app.reminder.ReminderScheduler
import com.xinotes.app.util.ChecklistParser
import com.xinotes.app.util.ImageStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * Заметка — это последовательность блоков: обычный текст, картинка, пункт чек-листа.
 */
private sealed class ContentBlock {
    data class TextBlock(val text: String) : ContentBlock()
    data class ImageBlock(val imageFile: String) : ContentBlock()
    data class ChecklistBlock(
        val checked: Boolean,
        val text: String,
        val itemId: Int? = null,
        val reminderAt: Long? = null,
        val repeatMode: String? = null
    ) : ContentBlock()
}

private val IMG_REGEX = Regex("""\[\[img:([^]]+)]]""")

private fun parseContent(content: String): List<ContentBlock> {
    val blocks = mutableListOf<ContentBlock>()

    fun flushTextSegment(segment: String) {
        if (segment.isEmpty()) return
        val lines = segment.split("\n")
        val buffer = StringBuilder()
        lines.forEachIndexed { i, rawLine ->
            val match = ChecklistParser.LINE_REGEX.find(rawLine)
            if (match != null) {
                if (buffer.isNotEmpty()) {
                    blocks.add(ContentBlock.TextBlock(buffer.toString()))
                    buffer.clear()
                }
                val checked = match.groupValues[1].lowercase() == "x"
                val itemId = match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull()
                val reminderAt = match.groupValues[3].takeIf { it.isNotEmpty() }?.toLongOrNull()
                val repeatMode = ChecklistParser.repeatCodeToMode(match.groupValues[4])
                blocks.add(ContentBlock.ChecklistBlock(checked, match.groupValues[5], itemId, reminderAt, repeatMode))
            } else {
                buffer.append(rawLine)
                if (i != lines.lastIndex) buffer.append("\n")
            }
        }
        if (buffer.isNotEmpty()) {
            blocks.add(ContentBlock.TextBlock(buffer.toString()))
        }
    }

    var lastIndex = 0
    IMG_REGEX.findAll(content).forEach { match ->
        flushTextSegment(content.substring(lastIndex, match.range.first))
        blocks.add(ContentBlock.ImageBlock(match.groupValues[1]))
        lastIndex = match.range.last + 1
    }
    flushTextSegment(content.substring(lastIndex))

    return ensureTrailingTextBlock(blocks)
}

private fun ensureTrailingTextBlock(blocks: List<ContentBlock>): List<ContentBlock> {
    return if (blocks.isEmpty() || blocks.last() !is ContentBlock.TextBlock) {
        blocks + ContentBlock.TextBlock("")
    } else blocks
}

private fun serializeContent(blocks: List<ContentBlock>): String = blocks.joinToString("") { block ->
    when (block) {
        is ContentBlock.TextBlock -> block.text
        is ContentBlock.ImageBlock -> "[[img:${block.imageFile}]]"
        is ContentBlock.ChecklistBlock -> buildString {
            append(if (block.checked) "[x]" else "[ ]")
            if (block.itemId != null) append("#${block.itemId}")
            if (block.reminderAt != null) append("@${block.reminderAt}")
            val repeatCode = ChecklistParser.repeatModeToCode(block.repeatMode)
            if (repeatCode.isNotEmpty()) append("!$repeatCode")
            append(" ")
            append(block.text)
            append("\n")
        }
    }
}

private fun commitNow(blocks: List<ContentBlock>, title: String, note: Note, onSave: (Note) -> Unit) {
    onSave(note.copy(content = serializeContent(blocks), title = title))
}

private val REPEAT_LABELS = mapOf(
    "DAILY" to "· каждый день",
    "WEEKLY" to "· каждую неделю",
    "YEARLY" to "· каждый год"
)

private fun reminderTimeText(timeMillis: Long, repeatMode: String?): String {
    val fmt = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    val repeatSuffix = repeatMode?.let { " ${REPEAT_LABELS[it] ?: ""}" } ?: ""
    return fmt.format(Date(timeMillis)) + repeatSuffix
}

/**
 * Сначала выбор даты (календарь), потом времени, потом — необязательный выбор повтора
 * (не повторять / каждый день / каждую неделю / каждый год). Общий диалог и для
 * напоминания всей заметки, и для напоминания отдельного пункта списка.
 */
private fun pickDateTime(context: Context, onPicked: (Long, String?) -> Unit) {
    val cal = Calendar.getInstance()
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
                    val options = arrayOf("Не повторять", "Каждый день", "Каждую неделю", "Каждый год")
                    val modes = arrayOf<String?>(null, "DAILY", "WEEKLY", "YEARLY")
                    AlertDialog.Builder(context)
                        .setTitle("Повторять напоминание?")
                        .setItems(options) { _, which -> onPicked(cal.timeInMillis, modes[which]) }
                        .show()
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
}

@Composable
fun NoteEditorScreen(
    note: Note,
    onSave: (Note) -> Unit,
    onSetReminder: (Note, Long, String?) -> Unit,
    onClearReminder: (Note) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saveJob by remember(note.id) { mutableStateOf<Job?>(null) }

    var blocks by remember(note.id) { mutableStateOf(parseContent(note.content)) }
    var focusedBlockIndex by remember(note.id) { mutableStateOf(0) }
    var titleText by remember(note.id) { mutableStateOf(note.title) }

    fun commitDebounced() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(500)
            commitNow(blocks, titleText, note, onSave)
        }
    }

    fun commitImmediate() {
        saveJob?.cancel()
        commitNow(blocks, titleText, note, onSave)
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val filename = ImageStore.importImage(context, uri)
            if (filename != null) {
                val mutable = blocks.toMutableList()
                val insertAt = (focusedBlockIndex + 1).coerceIn(0, mutable.size)
                mutable.add(insertAt, ContentBlock.ImageBlock(filename))
                blocks = ensureTrailingTextBlock(mutable)
                commitImmediate()
            } else {
                Toast.makeText(context, "Не удалось вставить картинку", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun insertChecklistItem() {
        val mutable = blocks.toMutableList()
        val insertAt = (focusedBlockIndex + 1).coerceIn(0, mutable.size)
        mutable.add(insertAt, ContentBlock.ChecklistBlock(checked = false, text = ""))
        blocks = ensureTrailingTextBlock(mutable)
        focusedBlockIndex = insertAt
        commitImmediate()
    }

    fun clearCompleted() {
        val filtered = blocks.filterNot { it is ContentBlock.ChecklistBlock && it.checked }
        blocks = ensureTrailingTextBlock(filtered)
        commitImmediate()
    }

    val hasCompleted = blocks.any { it is ContentBlock.ChecklistBlock && it.checked }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = titleText,
            onValueChange = { newTitle ->
                titleText = newTitle
                commitDebounced()
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            placeholder = { Text("Название заметки") },
            singleLine = false,
            textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color.Transparent
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasCompleted) {
                TextButton(onClick = { clearCompleted() }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Очистить выполненные")
                }
            } else {
                Box {}
            }

            Row {
                IconButton(onClick = { insertChecklistItem() }) {
                    Icon(Icons.Filled.PlaylistAddCheck, contentDescription = "Добавить пункт списка")
                }

                IconButton(onClick = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Filled.Image, contentDescription = "Вставить картинку")
                }

                IconButton(onClick = {
                    pickDateTime(context) { triggerAtMillis, repeatMode ->
                        onSetReminder(note, triggerAtMillis, repeatMode)
                    }
                }) {
                    Icon(
                        if (note.reminderAt != null) Icons.Filled.Alarm else Icons.Filled.AlarmOff,
                        contentDescription = "Напоминание"
                    )
                }
            }
        }

        // Видно, на какое время стоит напоминание всей заметки (и повторяется ли оно) —
        // раньше об этом можно было судить только по цвету иконки будильника.
        if (note.reminderAt != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clickable { onClearReminder(note) },
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    reminderTimeText(note.reminderAt, note.repeatMode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 12.dp)
        ) {
            blocks.forEachIndexed { index, block ->
                when (block) {
                    is ContentBlock.ImageBlock -> {
                        Box(modifier = Modifier.padding(vertical = 6.dp)) {
                            AsyncImage(
                                model = ImageStore.fileFor(context, block.imageFile),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)
                            )
                            // Кружок-подложка под крестиком — иначе на широкой/светлой картинке
                            // сам крестик может стать не виден или перекрыт содержимым фото.
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            ) {
                                TextButton(onClick = {
                                    ImageStore.fileFor(context, block.imageFile).delete()
                                    val mutable = blocks.toMutableList()
                                    mutable.removeAt(index)
                                    blocks = ensureTrailingTextBlock(mutable)
                                    commitImmediate()
                                }) { Text("✕", color = Color.White) }
                            }
                        }
                    }

                    is ContentBlock.ChecklistBlock -> {
                        val bringIntoViewRequester = remember { BringIntoViewRequester() }
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = block.checked,
                                    onCheckedChange = { newChecked ->
                                        val mutable = blocks.toMutableList()
                                        if (newChecked && block.reminderAt != null && block.itemId != null) {
                                            // Пункт отмечен выполненным раньше срока — напоминание
                                            // про него больше не нужно, отменяем.
                                            ReminderScheduler.cancel(context, block.itemId)
                                            mutable[index] = block.copy(
                                                checked = true, reminderAt = null, repeatMode = null
                                            )
                                        } else {
                                            mutable[index] = block.copy(checked = newChecked)
                                        }
                                        blocks = mutable
                                        commitImmediate()
                                    }
                                )
                                OutlinedTextField(
                                    value = block.text,
                                    onValueChange = { newText ->
                                        val mutable = blocks.toMutableList()
                                        mutable[index] = block.copy(text = newText)
                                        blocks = mutable
                                        focusedBlockIndex = index
                                        commitDebounced()
                                        scope.launch { bringIntoViewRequester.bringIntoView() }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .bringIntoViewRequester(bringIntoViewRequester)
                                        .onFocusChanged {
                                            if (it.isFocused) {
                                                focusedBlockIndex = index
                                                scope.launch { bringIntoViewRequester.bringIntoView() }
                                            }
                                        },
                                    singleLine = false,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        textDecoration = if (block.checked) TextDecoration.LineThrough else TextDecoration.None,
                                        color = if (block.checked)
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    ),
                                    placeholder = { Text("Пункт списка") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedBorderColor = Color.Transparent
                                    )
                                )
                                TextButton(onClick = {
                                    if (block.reminderAt != null) {
                                        ReminderScheduler.cancel(context, block.itemId!!)
                                        val mutable = blocks.toMutableList()
                                        mutable[index] = block.copy(reminderAt = null, repeatMode = null)
                                        blocks = mutable
                                        commitImmediate()
                                    } else {
                                        pickDateTime(context) { triggerAtMillis, repeatMode ->
                                            val id = block.itemId ?: Random.nextInt(1, Int.MAX_VALUE)
                                            val mutable = blocks.toMutableList()
                                            mutable[index] = block.copy(
                                                itemId = id, reminderAt = triggerAtMillis, repeatMode = repeatMode
                                            )
                                            blocks = mutable
                                            ReminderScheduler.schedule(
                                                context, id, note.id,
                                                block.text.ifBlank { "Пункт списка" },
                                                titleText.ifBlank { "Заметка" },
                                                triggerAtMillis,
                                                repeatMode,
                                                id
                                            )
                                            commitImmediate()
                                        }
                                    }
                                }) {
                                    Icon(
                                        if (block.reminderAt != null) Icons.Filled.Alarm else Icons.Filled.AlarmOff,
                                        contentDescription = "Напоминание для пункта",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                TextButton(onClick = {
                                    val mutable = blocks.toMutableList()
                                    if (block.reminderAt != null && block.itemId != null) {
                                        ReminderScheduler.cancel(context, block.itemId)
                                    }
                                    mutable.removeAt(index)
                                    blocks = ensureTrailingTextBlock(mutable)
                                    commitImmediate()
                                }) { Text("✕") }
                            }
                            // Видно, на какое время стоит напоминание именно этого пункта.
                            if (block.reminderAt != null) {
                                Text(
                                    reminderTimeText(block.reminderAt, block.repeatMode),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 48.dp)
                                )
                            }
                        }
                    }

                    is ContentBlock.TextBlock -> {
                        val bringIntoViewRequester = remember { BringIntoViewRequester() }
                        OutlinedTextField(
                            value = block.text,
                            onValueChange = { newText ->
                                val mutable = blocks.toMutableList()
                                mutable[index] = block.copy(text = newText)
                                blocks = mutable
                                focusedBlockIndex = index
                                commitDebounced()
                                scope.launch { bringIntoViewRequester.bringIntoView() }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(bringIntoViewRequester)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        focusedBlockIndex = index
                                        scope.launch { bringIntoViewRequester.bringIntoView() }
                                    }
                                },
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
}
