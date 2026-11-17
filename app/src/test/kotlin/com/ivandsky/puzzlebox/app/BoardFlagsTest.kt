package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The app host's flag-to-Boolean mapping for the readable board (nav-rethink B3): the theme gets
// one Boolean, true exactly when BoardTokens is in the live set. HintFirstMove never reaches it.
class BoardFlagsTest {

    @Test
    fun `readable exactly when BoardTokens is in the set`() {
        assertTrue(FakeReleaseFlags(ReleaseFlag.BoardTokens).enabled.value.readableBoard())
        assertFalse(FakeReleaseFlags().enabled.value.readableBoard())
    }

    @Test
    fun `other flags, HintFirstMove included, do not make the board readable`() {
        val others = ReleaseFlag.entries.filter { it != ReleaseFlag.BoardTokens }.toTypedArray()

        assertFalse(FakeReleaseFlags(*others).enabled.value.readableBoard())
    }

    @Test
    fun `both new flags ship on in release`() {
        assertTrue(ReleaseFlag.BoardTokens.releaseDefault)
        assertTrue(ReleaseFlag.HintFirstMove.releaseDefault)
    }
}
