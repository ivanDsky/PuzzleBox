package com.ivandsky.puzzlebox.common.flags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization (ADR-0022): a release build reads every flag as its release default, and nothing else. Pins
 * today's behavior before the flags move to their owner modules. Reads only the public API and pins no live flag
 * name, since flags are flipped and removed all the time.
 */
class ShippedReleaseFlagsTest {

    @Test
    fun `release reads every flag as its release default`() {
        val entries = ReleaseFlag.entries
        assertTrue("no release flag: move this test to a test-only flag", entries.isNotEmpty())
        assertTrue(
            "no release flag is on by default: add a test-only flag that is, so this test sees both values",
            entries.any { it.releaseDefault },
        )
        assertTrue(
            "no release flag is off by default: add a test-only flag that is, so this test sees both values",
            entries.any { !it.releaseDefault },
        )

        entries.forEach { assertEquals("$it", it.releaseDefault, ShippedReleaseFlags.isEnabled(it)) }
    }
}
