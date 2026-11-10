package com.ivandsky.puzzlebox.common.flags

import org.junit.Assert.assertFalse
import org.junit.Test

class ShippedReleaseFlagsTest {

    @Test
    fun `shipped flags exclude Competitions until its flip ticket`() {
        assertFalse(ShippedReleaseFlags.enabled.value.contains(ReleaseFlag.Competitions))
    }

    @Test
    fun `shipped flags exclude NavV2 until its flip ticket`() {
        assertFalse(ShippedReleaseFlags.enabled.value.contains(ReleaseFlag.NavV2))
    }
}
