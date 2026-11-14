package com.ivandsky.puzzlebox.history

import java.time.LocalDate
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class CalendarStateTest {
    private val today = LocalDate.of(2026, 11, 13)

    @Test
    fun `a day taps only with rows and never after today`() {
        val state = CalendarState(daysWithRows = setOf(today, today.plusDays(1)), today = today)

        assertTrue(state.canSelect(today))
        assertFalse(state.canSelect(today.minusDays(1)))
        assertFalse(state.canSelect(today.plusDays(1)))
    }

    @Test
    fun `nothing taps before the first emission`() {
        assertFalse(CalendarState(daysWithRows = setOf(today)).canSelect(today))
    }
}
