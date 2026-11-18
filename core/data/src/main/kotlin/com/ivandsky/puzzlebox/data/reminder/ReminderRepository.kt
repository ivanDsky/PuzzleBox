package com.ivandsky.puzzlebox.data.reminder

import com.ivandsky.puzzlebox.model.ReminderSettings
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

/**
 * The daily-reminder choice, per device (never cloud-synced). Settings writes it and the reminder runner
 * reads it; neither calls the other.
 */
interface ReminderRepository {

    /** The stored choice, again on every change; off at 7:00 PM until the player picks. */
    val settings: Flow<ReminderSettings>

    suspend fun setEnabled(enabled: Boolean)

    /** Stores [time] to the minute; seconds are dropped. */
    suspend fun setTime(time: LocalTime)
}
