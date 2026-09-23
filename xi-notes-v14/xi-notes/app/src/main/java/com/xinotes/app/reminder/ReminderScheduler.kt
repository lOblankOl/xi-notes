package com.xinotes.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * requestCode отделён от noteId — это позволяет нескольким напоминаниям (например,
 * у отдельных пунктов чек-листа внутри одной заметки) существовать одновременно и
 * не затирать друг друга.
 *
 * repeatMode ("DAILY"/"WEEKLY"/"YEARLY"/null) — если задан, ReminderReceiver сам
 * переназначит следующее срабатывание после текущего (см. ReminderReceiver).
 * itemId — только для напоминания на отдельный пункт чек-листа (null для напоминания
 * всей заметки целиком).
 */
object ReminderScheduler {
    fun schedule(
        context: Context,
        requestCode: Int,
        noteId: Long,
        title: String,
        preview: String,
        triggerAtMillis: Long,
        repeatMode: String? = null,
        itemId: Int? = null
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("noteId", noteId)
            putExtra("notificationId", requestCode)
            putExtra("title", title)
            putExtra("preview", preview)
            putExtra("triggerAtMillis", triggerAtMillis)
            if (repeatMode != null) putExtra("repeatMode", repeatMode)
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
