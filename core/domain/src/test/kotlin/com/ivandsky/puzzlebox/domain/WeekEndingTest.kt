package com.ivandsky.puzzlebox.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekEndingTest {
    @Test
    fun `the week is seven days, oldest first, ending today across a month edge`() {
        val today = LocalDate.of(2026, 3, 3)

        assertEquals(
            listOf("02-25", "02-26", "02-27", "02-28", "03-01", "03-02", "03-03"),
            weekEnding(today).map { it.toString().drop(5) },
        )
        assertEquals(WEEK_DAYS, weekEnding(today).size)
    }
}
