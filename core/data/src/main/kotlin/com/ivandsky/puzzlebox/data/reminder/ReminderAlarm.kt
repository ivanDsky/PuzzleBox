package com.ivandsky.puzzlebox.data.reminder

import java.time.Instant

/** The one pending wake-up of the daily reminder; the runner arms it and the system calls back at [schedule]'s instant. */
interface ReminderAlarm {

    /** Arms a wake-up at [at], replacing any pending one. */
    fun schedule(at: Instant)

    /** Drops the pending wake-up; nothing pending is fine. */
    fun cancel()
}
