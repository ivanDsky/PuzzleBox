package com.ivandsky.puzzlebox.common.flags

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Characterization (ADR-0022): a release build reads every declared flag as its release default, and nothing else.
 * Uses test-only flags, one on and one off by default, so it sees both values and pins no live flag, since flags are
 * flipped, moved to their owners and removed all the time.
 */
class ShippedReleaseFlagsTest {

    private class TestFlag(override val key: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
        override val label: String = key
    }

    @Test
    fun `release reads every flag as its release default`() {
        val entries = listOf(TestFlag("On", releaseDefault = true), TestFlag("Off", releaseDefault = false))

        entries.forEach { assertEquals(it.key, it.releaseDefault, ShippedReleaseFlags(entries).isEnabled(it)) }
    }
}
