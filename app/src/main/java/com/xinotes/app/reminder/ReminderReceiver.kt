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
import com.xinotes.app.util.ChecklistParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Текст уведомления собирается ЗАНОВО из базы в момент срабатывания, а не берётся
 * из того, что было передано при планировании — так, если пользователь отредактировал
 * заметку/пункт после того как поставил напоминание, придёт актуальный текст, а не
 * старый. Extras "title"/"preview" — только запасной вариант на случай, если заметку
 * или пункт не удастся найти (например, заметку успели удалить).
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra("noteId", -1L)
        val notificationId = intent.getIntExtra("notificationId", noteId.toInt())
        val itemId = intent.getIntExtra("itemId", -1).takeIf { it != -1 }
        val fallbackTitle = intent.getStringExtra("title") ?: "Заметка"
        val fallbackPreview = intent.getStringExtra("preview") ?: ""

        NotificationHelper.ensureChannel(context)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val note = AppDatabase.getInstance(context).noteDao().getById(noteId)
                val (notifTitle, notifPreview) = when {
                    note == null -> fallbackTitle to fallbackPreview
                    itemId != null -> {
                        val currentText = ChecklistParser.findItemText(note.content, itemId)
                            ?: fallbackTitle
                        currentText.ifBlank { "Пункт списка" } to note.title.ifBlank { "Заметка" }
                    }
                    else -> note.title.ifBlank { "Заметка" } to note.content.take(80)
                }
                showNotification(context, notificationId, noteId, notifTitle, notifPreview)
            } finally {
                pendingResult.finish()
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
