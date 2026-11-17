package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import org.junit.Assert.assertTrue
import org.junit.Test

// UnderlineTabs (nav-rethink 125) flipped on in 0.34.1: Games ships the underline strip with swipe.
class UnderlineTabsFlagTest {

    @Test
    fun `UnderlineTabs ships on in release`() {
        assertTrue(ReleaseFlag.UnderlineTabs.releaseDefault)
    }
}
