package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.play.flags.PlayFlag
import org.junit.Assert.assertFalse
import org.junit.Test

/** Pins that launcher shortcuts ship dark; the flip ticket deletes this. */
class AppShortcutsFlagTest {

    @Test
    fun `AppShortcuts ships off in release`() {
        assertFalse(PlayFlag.AppShortcuts.releaseDefault)
    }
}
