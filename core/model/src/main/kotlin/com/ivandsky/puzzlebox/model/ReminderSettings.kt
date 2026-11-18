package com.ivandsky.puzzlebox.model

import java.time.LocalTime

/** The player's daily-reminder choice on this device: whether it is on and the time of day (device zone) it fires. Off at 7:00 PM until chosen. */
data class ReminderSettings(
    val enabled: Boolean = false,
    val time: LocalTime = LocalTime.of(19, 0),
)
