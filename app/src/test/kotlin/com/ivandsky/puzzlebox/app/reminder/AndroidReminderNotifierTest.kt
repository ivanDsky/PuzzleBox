package com.ivandsky.puzzlebox.app.reminder

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.R
import com.ivandsky.puzzlebox.model.ReminderMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class AndroidReminderNotifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notifier = AndroidReminderNotifier(context)
    private val message = ReminderMessage("Today's puzzles are waiting", "Open today: Zip, Queens.")

    @Test
    fun `a post shows one notification with the title and body on the Daily reminder channel`() {
        notifier.show(message)

        val posted = shadowOf(manager).allNotifications.single()
        assertEquals(message.title, posted.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(message.body, posted.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(ReminderChannel.ID, posted.channelId)
        assertTrue(posted.flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    @Test
    fun `the channel is named Daily reminder with default importance`() {
        notifier.show(message)

        val channel = manager.getNotificationChannel(ReminderChannel.ID)
        assertEquals("Daily reminder", channel.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }

    @Test
    fun `the small icon is the bell drawable`() {
        notifier.show(message)

        assertEquals(R.drawable.ic_stat_reminder, shadowOf(manager).allNotifications.single().smallIcon.resId)
    }

    @Test
    fun `a second post replaces the first, so one notification is left`() {
        notifier.show(message)
        notifier.show(ReminderMessage("Today's puzzles are waiting", "Open today: Tango."))

        val posted = shadowOf(manager).allNotifications.single()
        assertEquals("Open today: Tango.", posted.extras.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun `tapping it launches the app the way the launcher icon does`() {
        notifier.show(message)

        val tap = shadowOf(shadowOf(manager).allNotifications.single().contentIntent)
        assertNotNull(tap)
        val intent = tap.savedIntent
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertTrue(Intent.CATEGORY_LAUNCHER in intent.categories)
        assertEquals(context.packageName, intent.component?.packageName ?: intent.`package`)
    }
}
