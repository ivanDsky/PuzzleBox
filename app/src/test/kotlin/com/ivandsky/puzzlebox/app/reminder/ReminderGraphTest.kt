package com.ivandsky.puzzlebox.app.reminder

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.PuzzleBoxApplication
import com.ivandsky.puzzlebox.data.reminder.NotificationAccess
import com.ivandsky.puzzlebox.data.reminder.ReminderAlarm
import com.ivandsky.puzzlebox.data.reminder.ReminderNotifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import android.os.Looper
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class ReminderGraphTest {

    @Test
    fun `the notifier and the access resolve off the real app graph as the Android ones`() {
        val koin = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }.koin
        try {
            assertTrue(koin.get<ReminderNotifier>() is AndroidReminderNotifier)
            assertTrue(koin.get<NotificationAccess>() is AndroidNotificationAccess)
        } finally {
            stopKoin()
        }
    }

    @Test
    fun `the alarm resolves as the Android one and the runner is off in the real graph`() {
        val koin = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }.koin
        try {
            assertTrue(koin.get<ReminderAlarm>() is AndroidReminderAlarm)
            assertFalse(koin.get<ReminderRunner>().enabled)
        } finally {
            stopKoin()
        }
    }

    @Test
    fun `starting the hooks with the flag off arms nothing and never creates the reminder preferences file`() {
        val application = RuntimeEnvironment.getApplication()
        val koin = startKoin {
            androidContext(application)
            modules(PuzzleBoxApplication.appModules)
        }.koin
        try {
            koin.get<ReminderAppHooks>().start()
            shadowOf(Looper.getMainLooper()).idle()

            assertFalse(application.filesDir.resolve("reminders.preferences_pb").exists())
        } finally {
            stopKoin()
        }
    }
}
