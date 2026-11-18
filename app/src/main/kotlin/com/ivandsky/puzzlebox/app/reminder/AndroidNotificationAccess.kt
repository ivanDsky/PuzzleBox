package com.ivandsky.puzzlebox.app.reminder

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.ivandsky.puzzlebox.data.reminder.NotificationAccess

/** Allowed when notifications are on for the app, the Android 13+ permission is held and the reminder channel is not blocked. */
class AndroidNotificationAccess(private val context: Context) : NotificationAccess {

    override fun isAllowed(): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        // The channel only exists after the first post; before that the system default (on) applies.
        val channel = manager.getNotificationChannel(ReminderChannel.ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }
}
