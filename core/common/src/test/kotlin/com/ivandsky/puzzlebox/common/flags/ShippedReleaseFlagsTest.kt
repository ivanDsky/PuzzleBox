package com.ivandsky.puzzlebox.common.flags

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShippedReleaseFlagsTest {

    @Test
    fun `shipped flags include Competitions since its flip`() {
        assertTrue(ShippedReleaseFlags.enabled.value.contains(ReleaseFlag.Competitions))
    }

    @Test
    fun `shipped flags include LinkedInAccount`() {
        assertTrue(ShippedReleaseFlags.enabled.value.contains(ReleaseFlag.LinkedInAccount))
    }

    @Test
    fun `shipped flags exclude FullReplay until its flip ticket`() {
        assertFalse(ShippedReleaseFlags.enabled.value.contains(ReleaseFlag.FullReplay))
    }

    @Test
    fun `shipped flags exclude CloudSync until its flip ticket`() {
        assertFalse(ShippedReleaseFlags.enabled.value.contains(ReleaseFlag.CloudSync))
    }

    @Test
    fun `shipped flags exclude LeaderboardHub until its flip ticket`() {
        assertFalse(ShippedReleaseFlags.enabled.value.contains(ReleaseFlag.LeaderboardHub))
    }
}
