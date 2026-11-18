package com.ivandsky.puzzlebox.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ivandsky.puzzlebox.data.reminder.ReminderAlarm
import java.time.Instant

/**
 * The one pending wake-up on the platform alarm service: an inexact RTC wake-up that may be minutes late and still fires in
 * idle, so it needs no exact-alarm permission. The fixed request code makes every [schedule] replace the last.
 */
class AndroidReminderAlarm(private val context: Context) : ReminderAlarm {

    private val manager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(at: Instant) {
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending())
    }

    override fun cancel() {
        manager.cancel(pending())
    }

    private fun pending(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val REQUEST_CODE = 1
    }
}
