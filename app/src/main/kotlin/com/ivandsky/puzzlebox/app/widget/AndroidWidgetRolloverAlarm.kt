package com.ivandsky.puzzlebox.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.Instant

/**
 * The platform alarm for the widget's rollover: an inexact RTC wake-up that may be minutes late and still fires in idle, so
 * it needs no exact-alarm permission. Its own fixed request code makes every [schedule] replace the last without touching the
 * reminder's alarm.
 */
class AndroidWidgetRolloverAlarm(private val context: Context) : WidgetRolloverAlarm {

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
        Intent(context, WidgetAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val REQUEST_CODE = 2
    }
}
