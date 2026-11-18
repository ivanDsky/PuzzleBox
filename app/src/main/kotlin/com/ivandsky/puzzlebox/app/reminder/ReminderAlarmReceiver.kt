package com.ivandsky.puzzlebox.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/** The reminder alarm went off: runs the fire path and keeps the process alive until it finishes. Registered by the manifest ticket. */
class ReminderAlarmReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        get<ReminderRunner>().fire(done = { pending?.finish() })
    }
}
