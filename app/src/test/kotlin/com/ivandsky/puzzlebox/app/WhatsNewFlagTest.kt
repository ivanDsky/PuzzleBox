package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import org.junit.Assert.assertFalse
import org.junit.Test

// WhatsNew (epic #1363) merges dark: no release build shows the Settings row until the flip PR sets it on.
class WhatsNewFlagTest {

    @Test
    fun `WhatsNew ships off in release`() {
        assertFalse(ReleaseFlag.WhatsNew.releaseDefault)
    }
}
