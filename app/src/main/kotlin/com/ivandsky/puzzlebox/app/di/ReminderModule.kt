package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.app.reminder.AndroidNotificationAccess
import com.ivandsky.puzzlebox.app.reminder.AndroidReminderAlarm
import com.ivandsky.puzzlebox.app.reminder.AndroidReminderNotifier
import com.ivandsky.puzzlebox.app.reminder.ReminderAppHooks
import com.ivandsky.puzzlebox.app.reminder.ReminderRunner
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.data.reminder.NotificationAccess
import com.ivandsky.puzzlebox.data.reminder.ReminderAlarm
import com.ivandsky.puzzlebox.data.reminder.ReminderNotifier
import com.ivandsky.puzzlebox.domain.ComputeNextReminder
import com.ivandsky.puzzlebox.domain.ComputeReminderMessage
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** The Android half of the daily reminder: posting it, asking whether posting is allowed, the alarm, and the runner that ties them. */
val reminderModule = module {
    single<ReminderNotifier> { AndroidReminderNotifier(androidContext()) }
    single<NotificationAccess> { AndroidNotificationAccess(androidContext()) }
    single<ReminderAlarm> { AndroidReminderAlarm(androidContext()) }
    single {
        ReminderRunner(
            enabled = getOrNull<ReleaseFlags>()?.isEnabled(ReleaseFlag.Reminders) == true,
            repository = get(),
            access = get(),
            alarm = get(),
            notifier = get(),
            // Resolved on the first fire, so a flag-off graph never builds today's set for it.
            todaySet = { get<ObserveTodaySet>()().first() },
            computeNext = ComputeNextReminder(),
            computeMessage = ComputeReminderMessage(),
            now = Instant::now,
            zone = ZoneId::systemDefault,
            scope = get<CoroutineScope>(),
        )
    }
    single { ReminderAppHooks(androidApplication(), get()) }
}
