package com.ivandsky.puzzlebox.common.flags

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShippedReleaseFlagsTest {

    @Test
    fun `shipped flags exclude Competitions until its flip ticket`() {
        assertFalse(ShippedReleaseFlags.enabled.value.contains(ReleaseFlag.Competitions))
    }

    @Test
    fun `shipped flags include NavV2 after its flip ticket`() {
        assertTrue(ShippedReleaseFlags.enabled.value.contains(ReleaseFlag.NavV2))
    }

    @Test
    fun `shipped flags exclude LinkedInAccount until its flip ticket`() {
        assertFalse(ShippedReleaseFlags.enabled.value.contains(ReleaseFlag.LinkedInAccount))
    }
}
