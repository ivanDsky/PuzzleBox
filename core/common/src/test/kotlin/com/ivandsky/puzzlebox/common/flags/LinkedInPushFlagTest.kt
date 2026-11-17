package com.ivandsky.puzzlebox.common.flags

import org.junit.Assert.assertFalse
import org.junit.Test

// LinkedInPush merges dark: every release build keeps posting off until the flip PR sets it on (ticket 09 flips this test with it).
class LinkedInPushFlagTest {

    @Test
    fun `LinkedInPush ships off in release`() {
        assertFalse(ReleaseFlag.LinkedInPush.releaseDefault)
    }
}
