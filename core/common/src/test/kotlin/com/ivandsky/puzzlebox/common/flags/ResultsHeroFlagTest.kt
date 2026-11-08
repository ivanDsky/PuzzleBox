package com.ivandsky.puzzlebox.common.flags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultsHeroFlagTest {

    @Test
    fun `ResultsHero is on in shipped builds`() {
        assertTrue(ReleaseFlag.ResultsHero in ShippedReleaseFlags.enabled.value)
    }

    @Test
    fun `ResultsHero is labelled for the debug Catalog`() {
        assertEquals("Results hero", ReleaseFlag.ResultsHero.label)
    }
}
