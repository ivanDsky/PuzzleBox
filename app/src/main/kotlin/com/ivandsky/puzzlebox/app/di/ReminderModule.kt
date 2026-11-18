package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.app.reminder.AndroidNotificationAccess
import com.ivandsky.puzzlebox.app.reminder.AndroidReminderNotifier
import com.ivandsky.puzzlebox.data.reminder.NotificationAccess
import com.ivandsky.puzzlebox.data.reminder.ReminderNotifier
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** The Android half of the daily reminder: posting it and asking whether posting is allowed. */
val reminderModule = module {
    single<ReminderNotifier> { AndroidReminderNotifier(androidContext()) }
    single<NotificationAccess> { AndroidNotificationAccess(androidContext()) }
}
