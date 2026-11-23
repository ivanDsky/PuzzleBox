package com.ivandsky.puzzlebox.insights.hub

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which hub tiles play their entry motion: the first screenful, never a tile scrolled in later. */
class HubEntryTest {

    @Test
    fun `the first screenful of tiles plays`() {
        assertTrue((0 until ENTERING_TILES).all { entersWithGrid(it) })
    }

    @Test
    fun `a tile past the first screenful is static`() {
        assertFalse(entersWithGrid(ENTERING_TILES))
        assertFalse(entersWithGrid(40))
    }
}
