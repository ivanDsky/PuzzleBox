package com.ivandsky.puzzlebox.data.leaderboard

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A22's Final rule against ADR-0024's server window: the rules accept an entry for the server's
 * UTC date −2…+1, so a board a device calls Final must lie before that window even when the
 * device's date is a day off the server's.
 */
class BoardFinalityTest {

    private val serverToday = LocalDate.of(2026, 11, 13)

    /** ADR-0024 decision 2: the days the rules still accept an entry for. */
    private val serverWindow = (-2L..1L).map(serverToday::plusDays)

    private fun board(day: LocalDate) = LeaderboardRef("zip", day)

    @Test
    fun `a board more than three days behind the UTC date is final`() {
        assertTrue(board(serverToday.minusDays(4)).isFinal(serverToday))
        assertTrue(board(serverToday.minusDays(40)).isFinal(serverToday))
    }

    @Test
    fun `a board within three days of the UTC date is open`() {
        assertFalse(board(serverToday.minusDays(3)).isFinal(serverToday))
        assertFalse(board(serverToday).isFinal(serverToday))
        assertFalse(board(serverToday.plusDays(1)).isFinal(serverToday))
    }

    @Test
    fun `no day the server still accepts is final on a device date within a day of the server's`() {
        for (skew in -1L..1L) {
            val deviceToday = serverToday.plusDays(skew)
            val writableFinals = serverWindow.filter { board(it).isFinal(deviceToday) }
            assertEquals("device date $skew day(s) off the server", emptyList<LocalDate>(), writableFinals)
        }
    }

    @Test
    fun `every game has at most four open days up to the UTC date`() {
        val openDays = (0L..10L).map(serverToday::minusDays).filterNot { board(it).isFinal(serverToday) }
        assertEquals((0L..3L).map(serverToday::minusDays), openDays)
    }

    @Test
    fun `the margin is one day - a device date two days ahead calls a writable day final`() {
        val deviceToday = serverToday.plusDays(2)
        assertEquals(listOf(serverToday.minusDays(2)), serverWindow.filter { board(it).isFinal(deviceToday) })
    }
}
