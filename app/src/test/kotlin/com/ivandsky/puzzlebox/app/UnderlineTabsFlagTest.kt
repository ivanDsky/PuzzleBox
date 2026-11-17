package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import org.junit.Assert.assertFalse
import org.junit.Test

// UnderlineTabs (nav-rethink 125) merges dark: Games keeps its pill row in every release build until the flip.
class UnderlineTabsFlagTest {

    @Test
    fun `UnderlineTabs ships off in release`() {
        assertFalse(ReleaseFlag.UnderlineTabs.releaseDefault)
    }
}
