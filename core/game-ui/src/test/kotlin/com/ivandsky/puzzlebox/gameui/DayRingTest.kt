package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.designsystem.AccentSlot
import org.junit.Assert.assertEquals
import org.junit.Test

class DayRingTest {

    private val zip = GameBrand.Neutral.copy(accent = AccentSlot.Blue)
    private val tango = GameBrand.Neutral.copy(accent = AccentSlot.Orange)

    @Test
    fun `solved brands come first, in order, then empty segments up to total`() {
        assertEquals(listOf(tango, zip, null), dayRingFills(listOf(tango, zip), total = 3))
    }

    @Test
    fun `a day with no solves is all empty segments`() {
        assertEquals(listOf(null, null, null), dayRingFills(emptyList(), total = 3))
    }

    @Test
    fun `a total below the solve count still shows every solve`() {
        assertEquals(listOf(zip, tango), dayRingFills(listOf(zip, tango), total = 1))
    }
}
