package com.ivandsky.puzzlebox.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/** The device booted or the app was updated, which dropped the alarm: reconciles it. Registered by the manifest ticket. */
class ReminderRebootReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        get<ReminderRunner>().refresh(done = { pending?.finish() })
    }
}
