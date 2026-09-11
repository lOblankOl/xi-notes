package com.xinotes.app.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationHelper {
    const val CHANNEL_ID = "xi_notes_reminders"

    /**
     * IMPORTANCE_HIGH обязателен — именно он даёт то самое поведение "как в Telegram":
     * уведомление всплывает сверху экрана (heads-up), со свайпом для закрытия и тапом
     * для открытия нужной заметки. Без HIGH система покажет его тихо, без всплытия.
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Напоминания о заметках",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Всплывающие напоминания по заметкам Ξ-notes"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
