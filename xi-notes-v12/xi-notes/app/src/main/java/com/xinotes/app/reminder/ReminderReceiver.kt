package com.xinotes.app.reminder

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.xinotes.app.MainActivity
import com.xinotes.app.R
import com.xinotes.app.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra("noteId", -1L)
        val notificationId = intent.getIntExtra("notificationId", noteId.toInt())
        val itemId = intent.getIntExtra("itemId", -1).takeIf { it != -1 }
        val title = intent.getStringExtra("title") ?: "Заметка"
        val preview = intent.getStringExtra("preview") ?: ""
        val repeatMode = intent.getStringExtra("repeatMode")
        val originalTrigger = intent.getLongExtra("triggerAtMillis", System.currentTimeMillis())

        NotificationHelper.ensureChannel(context)
        showNotification(context, notificationId, noteId, title, preview)

        // Циклическое напоминание — сразу планируем следующее срабатывание и
        // сохраняем новое время в базе, чтобы приложение показывало актуальное
        // "следующее" время, а не то, что уже прошло.
        if (repeatMode != null) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val nextTrigger = nextOccurrence(originalTrigger, repeatMode)
                    ReminderScheduler.schedule(
                        context, notificationId, noteId, title, preview, nextTrigger, repeatMode, itemId
                    )
                    persistNextTrigger(context, noteId, itemId, nextTrigger)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun nextOccurrence(fromMillis: Long, repeatMode: String): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = fromMillis }
        when (repeatMode) {
            "DAILY" -> cal.add(Calendar.DAY_OF_YEAR, 1)
            "WEEKLY" -> cal.add(Calendar.DAY_OF_YEAR, 7)
            "YEARLY" -> cal.add(Calendar.YEAR, 1)
        }
        return cal.timeInMillis
    }

    private suspend fun persistNextTrigger(context: Context, noteId: Long, itemId: Int?, nextTrigger: Long) {
        val noteDao = AppDatabase.getInstance(context).noteDao()
        val note = noteDao.getById(noteId) ?: return
        if (itemId == null) {
            noteDao.update(note.copy(reminderAt = nextTrigger))
        } else {
            val newContent = replaceItemReminderTime(note.content, itemId, nextTrigger)
            noteDao.update(note.copy(content = newContent))
        }
    }

    /** Заменяет время напоминания у конкретного пункта чек-листа в тексте заметки, сохраняя остальное как есть. */
    private fun replaceItemReminderTime(content: String, itemId: Int, newTrigger: Long): String {
        val regex = com.xinotes.app.util.ChecklistParser.LINE_REGEX
        return content.split("\n").joinToString("\n") { line ->
            val match = regex.find(line) ?: return@joinToString line
            val id = match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull()
            if (id != itemId) return@joinToString line
            val checked = match.groupValues[1]
            val repeatCode = match.groupValues[4]
            val text = match.groupValues[5]
            buildString {
                append("[").append(checked).append("]")
                append("#").append(itemId)
                append("@").append(newTrigger)
                if (repeatCode.isNotEmpty()) append("!").append(repeatCode)
                append(" ").append(text)
            }
        }
    }

    private fun showNotification(
        context: Context,
        notificationId: Int,
        noteId: Long,
        title: String,
        preview: String
    ) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("openNoteId", noteId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(preview)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
