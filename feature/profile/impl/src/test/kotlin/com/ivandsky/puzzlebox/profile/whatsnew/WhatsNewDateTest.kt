package com.ivandsky.puzzlebox.profile.whatsnew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WhatsNewDateTest {

    @Test
    fun `an ISO date reads as day, short month and year`() {
        assertEquals("18 Nov 2026", formatReleaseDate("2026-11-18"))
    }

    @Test
    fun `the day has no leading zero and every month has its own name`() {
        assertEquals("5 Jan 2026", formatReleaseDate("2026-01-05"))
        val names = (1..12).map { formatReleaseDate("2026-%02d-01".format(it)).split(' ')[1] }
        assertEquals(listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"), names)
    }

    @Test
    fun `malformed input throws and is not guessed`() {
        listOf("", "2026-11", "18 Nov 2026", "2026-13-01", "2026-00-10", "2026-11-32", "2026-1-5", " 2026-11-18").forEach {
            assertThrows("`$it`", IllegalArgumentException::class.java) { formatReleaseDate(it) }
        }
    }
}
