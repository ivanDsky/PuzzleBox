package com.ivandsky.puzzlebox.app.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import com.ivandsky.puzzlebox.app.R
import com.ivandsky.puzzlebox.data.reminder.ReminderNotifier
import com.ivandsky.puzzlebox.model.ReminderMessage

/** Posts the reminder as a plain notification that opens the app the way the launcher icon does. */
class AndroidReminderNotifier(private val context: Context) : ReminderNotifier {

    override fun show(message: ReminderMessage) {
        val manager = context.getSystemService(NotificationManager::class.java)
        // Creating an existing channel changes nothing, so a rename or a player's own settings survive.
        manager.createNotificationChannel(
            NotificationChannel(ReminderChannel.ID, ReminderChannel.NAME, NotificationManager.IMPORTANCE_DEFAULT),
        )
        val notification = Notification.Builder(context, ReminderChannel.ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(message.title)
            .setContentText(message.body)
            .setAutoCancel(true)
            .setContentIntent(launchIntent())
            .build()
        // One fixed id: a second post replaces the first.
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun launchIntent(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private companion object {
        const val NOTIFICATION_ID = 1
    }
}
