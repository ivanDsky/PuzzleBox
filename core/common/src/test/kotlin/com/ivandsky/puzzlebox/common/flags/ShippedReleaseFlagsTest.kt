package com.ivandsky.puzzlebox.common.flags

import org.junit.Assert.assertFalse
import org.junit.Test

class ShippedReleaseFlagsTest {

    @Test
    fun `shipped flags exclude HintParity until its flip ticket`() {
        assertFalse(ShippedReleaseFlags.enabled.value.contains(ReleaseFlag.HintParity))
    }
}
