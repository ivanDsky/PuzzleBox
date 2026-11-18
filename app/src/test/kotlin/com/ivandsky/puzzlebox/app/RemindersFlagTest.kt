package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import org.junit.Assert.assertFalse
import org.junit.Test

// Reminders (#1169) merges dark: every release build posts no reminder until the flip PR sets it on.
class RemindersFlagTest {

    @Test
    fun `Reminders ships off in release`() {
        assertFalse(ReleaseFlag.Reminders.releaseDefault)
    }
}
