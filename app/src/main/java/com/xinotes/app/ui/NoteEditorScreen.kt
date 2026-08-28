package com.xinotes.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.xinotes.app.data.Note
import com.xinotes.app.reminder.ReminderScheduler
import com.xinotes.app.util.ChecklistParser
import com.xinotes.app.util.FileStore
import com.xinotes.app.util.ImageStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.random.Random

/**
 * Заметка — это последовательность блоков трёх видов, в том порядке, в котором их
 * расположил пользователь:
 *  - TextBlock — обычный многострочный текст;
 *  - ImageBlock — картинка (маркер [[img:filename]] в content);
 *  - ChecklistBlock — один пункт списка с чекбоксом (строка вида "[ ] текст" / "[x] текст").
 * Всё это по-прежнему одна обычная текстовая заметка — чек-пункты не отдельный тип заметки,
 * а просто особые строки внутри того же текста, как и просили: можно свободно перемежать
 * обычный текст, картинки и чекбоксы в одной заметке.
 */
private sealed class ContentBlock {
    data class TextBlock(val text: String) : ContentBlock()
    data class ImageBlock(val imageFile: String) : ContentBlock()
    // Произвольный файл (не картинка) — например .apk, документ и т.д. storedName — как
    // файл лежит на диске (случайное имя), displayName — как он назывался у пользователя
    // и что показываем в заметке, как файл-вложение в Telegram.
    data class FileAttachmentBlock(val storedName: String, val displayName: String) : ContentBlock()
    // itemId — стабильный номер пункта, назначается один раз (при первой установке
    // напоминания) и хранится прямо в тексте, чтобы напоминание не "слетало" при
    // редактировании/перестановке пунктов. reminderAt — время именно ЭТОГО пункта,
    // отдельно от напоминания всей заметки.
    data class ChecklistBlock(
        val checked: Boolean,
        val text: String,
        val itemId: Int? = null,
        val reminderAt: Long? = null
    ) : ContentBlock()
}

private val ATTACHMENT_REGEX = Regex("""\[\[img:([^]]+)]]|\[\[file:([^:\]]+)::([^\]]+)]]""")

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
                blocks.add(ContentBlock.ChecklistBlock(checked, match.groupValues[4], itemId, reminderAt))
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
    ATTACHMENT_REGEX.findAll(content).forEach { match ->
        flushTextSegment(content.substring(lastIndex, match.range.first))
        if (match.groupValues[1].isNotEmpty()) {
            blocks.add(ContentBlock.ImageBlock(match.groupValues[1]))
        } else {
            blocks.add(ContentBlock.FileAttachmentBlock(match.groupValues[2], match.groupValues[3]))
        }
        lastIndex = match.range.last + 1
    }
    flushTextSegment(content.substring(lastIndex))

    return ensureTrailingTextBlock(blocks)
}

/** В конце всегда должен быть текстовый блок — место, куда можно просто печатать дальше. */
private fun ensureTrailingTextBlock(blocks: List<ContentBlock>): List<ContentBlock> {
    return if (blocks.isEmpty() || blocks.last() !is ContentBlock.TextBlock) {
        blocks + ContentBlock.TextBlock("")
    } else blocks
}

private fun serializeContent(blocks: List<ContentBlock>): String = blocks.joinToString("") { block ->
    when (block) {
        is ContentBlock.TextBlock -> block.text
        is ContentBlock.ImageBlock -> "[[img:${block.imageFile}]]"
        is ContentBlock.FileAttachmentBlock -> "[[file:${block.storedName}::${block.displayName}]]"
        is ContentBlock.ChecklistBlock -> buildString {
            append(if (block.checked) "[x]" else "[ ]")
            if (block.itemId != null) append("#${block.itemId}")
            if (block.reminderAt != null) append("@${block.reminderAt}")
            append(" ")
            append(block.text)
            append("\n")
        }
    }
}

private fun commitNow(blocks: List<ContentBlock>, title: String, note: Note, onSave: (Note) -> Unit) {
    onSave(note.copy(content = serializeContent(blocks), title = title))
}

/** Сначала выбор даты (календарь), потом времени — общий диалог для заметки и для пунктов списка. */
private fun pickDateTime(context: android.content.Context, onPicked: (Long) -> Unit) {
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
                    onPicked(cal.timeInMillis)
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

/**
 * Экран редактирования одной заметки.
 *
 * Автосохранение текста — с debounce ~0.5 сек после паузы в наборе, чтобы не писать на
 * диск при каждом нажатии клавиши. Вставка/удаление картинки, чекбокса, переключение
 * галочки — это структурные изменения, сохраняются сразу.
 *
 * Страховка от потери текста: если пользователь уйдёт с заметки или свернёт приложение
 * ДО того как сработает отложенное сохранение — несохранённые изменения принудительно
 * сохраняются немедленно (см. DisposableEffect ниже), а не пропадают.
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

    val commitImmediateRef = rememberUpdatedState { commitImmediate() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(note.id, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                commitImmediateRef.value()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            commitImmediateRef.value()
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: ""
            val mutable = blocks.toMutableList()
            val insertAt = (focusedBlockIndex + 1).coerceIn(0, mutable.size)
            if (mime.startsWith("image/")) {
                val filename = ImageStore.importImage(context, uri)
                if (filename != null) {
                    mutable.add(insertAt, ContentBlock.ImageBlock(filename))
                    blocks = ensureTrailingTextBlock(mutable)
                    commitImmediate()
                } else {
                    Toast.makeText(context, "Не удалось вставить картинку", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Видео (и всё, что не картинка) — вставляем как файл-вложение, без
                // декодирования: открывается системным плеером по тапу, как в Telegram.
                val stored = FileStore.importFile(context, uri)
                if (stored != null) {
                    mutable.add(insertAt, ContentBlock.FileAttachmentBlock(stored.storedName, stored.displayName))
                    blocks = ensureTrailingTextBlock(mutable)
                    commitImmediate()
                } else {
                    Toast.makeText(context, "Не удалось вставить файл", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val stored = FileStore.importFile(context, uri)
            if (stored != null) {
                val mutable = blocks.toMutableList()
                val insertAt = (focusedBlockIndex + 1).coerceIn(0, mutable.size)
                mutable.add(insertAt, ContentBlock.FileAttachmentBlock(stored.storedName, stored.displayName))
                blocks = ensureTrailingTextBlock(mutable)
                commitImmediate()
            } else {
                Toast.makeText(context, "Не удалось вставить файл", Toast.LENGTH_SHORT).show()
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
            singleLine = true,
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
                // Добавить пункт списка (чекбокс) — вставляется следующей строкой после
                // текущей позиции курсора/последнего тронутого блока.
                IconButton(onClick = { insertChecklistItem() }) {
                    Icon(Icons.Filled.PlaylistAddCheck, contentDescription = "Добавить пункт списка")
                }

                var showAttachMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showAttachMenu = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Вставить")
                    }
                    DropdownMenu(expanded = showAttachMenu, onDismissRequest = { showAttachMenu = false }) {
                        DropdownMenuItem(text = { Text("Фото / видео") }, onClick = {
                            showAttachMenu = false
                            mediaPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        })
                        DropdownMenuItem(text = { Text("Файл") }, onClick = {
                            showAttachMenu = false
                            filePicker.launch(arrayOf("*/*"))
                        })
                    }
                }

                IconButton(onClick = {
                    // Сначала выбор даты (календарь), потом времени — напоминание может
                    // стоять на любой день вперёд, не только на сегодня.
                    pickDateTime(context) { triggerAtMillis -> onSetReminder(note, triggerAtMillis) }
                }) {
                    Icon(
                        if (note.reminderAt != null) Icons.Filled.Alarm else Icons.Filled.AlarmOff,
                        contentDescription = "Напоминание"
                    )
                }
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
                                modifier = Modifier.fillMaxWidth()
                            )
                            TextButton(
                                onClick = {
                                    ImageStore.fileFor(context, block.imageFile).delete()
                                    val mutable = blocks.toMutableList()
                                    mutable.removeAt(index)
                                    blocks = ensureTrailingTextBlock(mutable)
                                    commitImmediate()
                                },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) { Text("✕") }
                        }
                    }

                    is ContentBlock.FileAttachmentBlock -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { FileStore.openExternally(context, block.storedName, block.displayName) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.InsertDriveFile, contentDescription = null)
                            Text(
                                block.displayName,
                                modifier = Modifier.weight(1f).padding(start = 8.dp),
                                maxLines = 1
                            )
                            TextButton(onClick = {
                                FileStore.fileFor(context, block.storedName).delete()
                                val mutable = blocks.toMutableList()
                                mutable.removeAt(index)
                                blocks = ensureTrailingTextBlock(mutable)
                                commitImmediate()
                            }) { Text("✕") }
                        }
                    }

                    is ContentBlock.ChecklistBlock -> {
                        val bringIntoViewRequester = remember { BringIntoViewRequester() }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = block.checked,
                                onCheckedChange = { newChecked ->
                                    val mutable = blocks.toMutableList()
                                    mutable[index] = block.copy(checked = newChecked)
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
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .bringIntoViewRequester(bringIntoViewRequester)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            focusedBlockIndex = index
                                            // Подтягиваем именно эту строку в видимую область над
                                            // клавиатурой — сама по себе прокрутка этого не делает,
                                            // она только освобождает место (imePadding), но не "едет"
                                            // к нужной строке автоматически.
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
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent
                                )
                            )
                            TextButton(onClick = {
                                if (block.reminderAt != null) {
                                    // Уже стоит напоминание на этот пункт — тап снимает его.
                                    ReminderScheduler.cancel(context, block.itemId!!)
                                    val mutable = blocks.toMutableList()
                                    mutable[index] = block.copy(reminderAt = null)
                                    blocks = mutable
                                    commitImmediate()
                                } else {
                                    pickDateTime(context) { triggerAtMillis ->
                                        val id = block.itemId ?: Random.nextInt(1, Int.MAX_VALUE)
                                        val mutable = blocks.toMutableList()
                                        mutable[index] = block.copy(itemId = id, reminderAt = triggerAtMillis)
                                        blocks = mutable
                                        ReminderScheduler.schedule(
                                            context, id, note.id,
                                            block.text.ifBlank { "Пункт списка" },
                                            titleText.ifBlank { "Заметка" },
                                            triggerAtMillis,
                                            itemId = id
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
