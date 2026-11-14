package com.ivandsky.puzzlebox.common.flags

import org.junit.Assert.assertTrue
import org.junit.Test

class ShippedReleaseFlagsTest {

    @Test
    fun `shipped flags include LeaderboardHub`() {
        assertTrue(ShippedReleaseFlags.enabled.value.contains(ReleaseFlag.LeaderboardHub))
    }
}
