package com.ivandsky.puzzlebox.common.flags

import org.junit.Assert.assertTrue
import org.junit.Test

// LinkedInPush shipped in 0.35.0 (ticket 09 flip, user-approved 2026-11-17); this pins the release default until the cleanup ticket removes the flag.
class LinkedInPushFlagTest {

    @Test
    fun `LinkedInPush ships on in release`() {
        assertTrue(ReleaseFlag.LinkedInPush.releaseDefault)
    }
}
