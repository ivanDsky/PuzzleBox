package com.ivandsky.puzzlebox.common.flags

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Characterization (ADR-0022): a release build turns on exactly the flags whose release default is on, and nothing
 * else. Pins today's behavior before the flags move to their owner modules.
 */
class ShippedReleaseFlagsTest {

    @Test
    fun `release turns on exactly the flags whose release default is on`() {
        assertEquals(ReleaseFlag.entries.filter { it.releaseDefault }.toSet(), ShippedReleaseFlags.enabled.value)
    }

    @Test
    fun `a flag off by default reads off in release`() {
        ReleaseFlag.entries.filterNot { it.releaseDefault }.forEach {
            assertEquals("$it", false, ShippedReleaseFlags.isEnabled(it))
        }
    }
}
