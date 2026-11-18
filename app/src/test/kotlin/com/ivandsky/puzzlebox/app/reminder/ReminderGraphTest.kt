package com.ivandsky.puzzlebox.app.reminder

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.PuzzleBoxApplication
import com.ivandsky.puzzlebox.data.reminder.NotificationAccess
import com.ivandsky.puzzlebox.data.reminder.ReminderNotifier
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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
}
