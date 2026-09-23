package com.xinotes.app

import android.app.Application
import com.xinotes.app.reminder.NotificationHelper

class XiNotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
    }
}
