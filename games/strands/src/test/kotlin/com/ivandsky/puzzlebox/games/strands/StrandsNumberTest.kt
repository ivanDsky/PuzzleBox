package com.ivandsky.puzzlebox.games.strands

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StrandsNumberTest {

    private fun number(date: String) = StrandsNumber.of(LocalDate.parse(date))

    @Test
    fun `the epoch is 1 and the next day 2`() {
        assertEquals(1, number("2024-03-04"))
        assertEquals(2, number("2024-03-05"))
    }

    @Test
    fun `numbers run on across the year boundary`() {
        assertEquals(303, number("2024-12-31"))
        assertEquals(304, number("2025-01-01"))
    }

    @Test
    fun `today is 977`() {
        assertEquals(977, number("2026-11-05"))
    }

    @Test
    fun `leap days count`() {
        assertEquals(number("2028-02-28") + 1, number("2028-02-29"))
        assertEquals(number("2028-02-29") + 1, number("2028-03-01"))
        assertEquals(1458, number("2028-02-29"))
    }
}
