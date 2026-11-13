package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.designsystem.AccentSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

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

    @Test
    fun `a day is not frozen unless the caller says so`() {
        assertFalse(WeekDay(LocalDate.of(2026, 11, 2), listOf(zip), isToday = false).frozen)
    }

    @Test
    fun `a frozen day keeps its ring segments`() {
        val solved = listOf(tango)
        val frozen = WeekDay(LocalDate.of(2026, 11, 2), solved, isToday = false, frozen = true)
        assertEquals(listOf(tango, null, null), dayRingFills(frozen.solved, total = 3))
    }

    @Test
    fun `freeze count label is singular for one and plural otherwise`() {
        assertEquals("0 freezes", freezeCountLabel(0))
        assertEquals("1 freeze", freezeCountLabel(1))
        assertEquals("5 freezes", freezeCountLabel(5))
    }
}
