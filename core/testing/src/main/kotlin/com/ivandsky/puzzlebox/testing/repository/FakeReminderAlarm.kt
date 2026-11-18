package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.reminder.ReminderAlarm
import java.time.Instant

/** Records the pending instant like the system alarm would hold it: null when none, replaced on each schedule. */
class FakeReminderAlarm : ReminderAlarm {

    var scheduled: Instant? = null

    var cancelCount: Int = 0

    override fun schedule(at: Instant) {
        scheduled = at
    }

    override fun cancel() {
        scheduled = null
        cancelCount++
    }
}
