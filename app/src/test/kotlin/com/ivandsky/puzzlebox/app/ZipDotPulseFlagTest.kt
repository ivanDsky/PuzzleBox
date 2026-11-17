package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import org.junit.Assert.assertFalse
import org.junit.Test

// ZipDotPulse (backlog #1151) merges dark: every release build keeps Zip's ring until the flip PR sets it on.
class ZipDotPulseFlagTest {

    @Test
    fun `ZipDotPulse ships off in release`() {
        assertFalse(ReleaseFlag.ZipDotPulse.releaseDefault)
    }
}
