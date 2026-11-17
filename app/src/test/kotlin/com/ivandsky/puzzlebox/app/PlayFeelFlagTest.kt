package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import org.junit.Assert.assertFalse
import org.junit.Test

// PlayFeel (spec A19) merges dark: every release build keeps the legacy play feel until the flip PR sets it on.
class PlayFeelFlagTest {

    @Test
    fun `PlayFeel ships off in release`() {
        assertFalse(ReleaseFlag.PlayFeel.releaseDefault)
    }
}
