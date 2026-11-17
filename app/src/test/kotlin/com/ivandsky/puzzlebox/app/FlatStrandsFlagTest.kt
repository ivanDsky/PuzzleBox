package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import org.junit.Assert.assertFalse
import org.junit.Test

// FlatStrands (nav-rethink 110) merges dark: Strands keeps its lipped look in every release build until its own flip.
class FlatStrandsFlagTest {

    @Test
    fun `FlatStrands ships off in release`() {
        assertFalse(ReleaseFlag.FlatStrands.releaseDefault)
    }
}
