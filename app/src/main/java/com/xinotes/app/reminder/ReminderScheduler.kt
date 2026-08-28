package com.xinotes.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object ReminderScheduler {
    /**
     * itemId передаётся только для напоминания на отдельный пункт чек-листа — по нему
     * ReminderReceiver в момент срабатывания заново прочитает АКТУАЛЬНЫЙ текст пункта
     * из базы, а не тот, что был на момент установки напоминания (title/preview здесь —
     * это просто запасной вариант на случай, если пункт/заметку не удастся найти).
     */
    fun schedule(
        context: Context,
        requestCode: Int,
        noteId: Long,
        title: String,
        preview: String,
        triggerAtMillis: Long,
        itemId: Int? = null
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("noteId", noteId)
            putExtra("notificationId", requestCode)
            putExtra("title", title)
            putExtra("preview", preview)
            if (itemId != null) putExtra("itemId", itemId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    fun cancel(context: Context, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
