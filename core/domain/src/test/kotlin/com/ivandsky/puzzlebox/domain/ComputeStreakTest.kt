package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Solved-day streak (A8): consecutive solved puzzle days anchored at today or yesterday;
 * the best streak is the longest run anywhere.
 */
class ComputeStreakTest {

    // 12:00 UTC on Nov 10 is 04:00 Nov 10 in Los Angeles: the puzzle day is Nov 10.
    private val clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC)
    private val registry = TestGames.registry(listOf(GameId("zip")))
    private val streak = ComputeStreak(GetPuzzleDay(registry, clock))

    private val today = LocalDate.of(2026, 11, 10)
    private val yesterday = today.minusDays(1)

    @Test
    fun `no solves is no streak`() {
        assertEquals(0, streak(emptyList()).current)
    }

    @Test
    fun `solved today and yesterday is two`() {
        assertEquals(2, streak(listOf(today, yesterday)).current)
    }

    @Test
    fun `solved yesterday only is one`() {
        assertEquals(1, streak(listOf(yesterday)).current)
    }

    @Test
    fun `last solve two days ago is held by a freeze that counts as a day`() {
        assertEquals(3, streak(listOf(today.minusDays(2), today.minusDays(3))).current)
    }

    @Test
    fun `a gap longer than the freezes held is no streak`() {
        // The two starting freezes cover a missed day each; three missed days outrun them.
        assertEquals(0, streak(listOf(today.minusDays(4), today.minusDays(5))).current)
    }

    @Test
    fun `opened but unsolved days are ignored`() {
        // The caller passes only solved days; an opened-unsolved yesterday is a missed day a freeze
        // covers, and the covered day counts.
        val solved = listOf(today, today.minusDays(2))
        assertEquals(3, streak(solved).current)
    }

    @Test
    fun `duplicate days collapse`() {
        assertEquals(2, streak(listOf(today, today, yesterday, yesterday)).current)
    }

    @Test
    fun `no solves is no best streak`() {
        assertEquals(0, streak(emptyList()).best)
    }

    @Test
    fun `the best streak is the longest run, wherever it falls`() {
        val oldRun = (10L..13L).map { today.minusDays(it) }
        assertEquals(4, streak(oldRun + listOf(yesterday, today)).best)
    }

    @Test
    fun `the best streak ignores order and duplicates`() {
        assertEquals(3, streak(listOf(today, today.minusDays(2), yesterday, today, yesterday)).best)
    }

    @Test
    fun `a day covered by a freeze counts toward the streak and the best`() {
        // Solve 3 days, miss 1 covered by a freeze, solve the next day: 5, not 4.
        val solved = listOf(4L, 3L, 2L, 0L).map(today::minusDays)
        assertEquals(5, streak(solved).current)
        assertEquals(5, streak(solved).best)
    }

    @Test
    fun `a gap longer than the freezes held counts nothing and breaks to the next solve`() {
        // Three missed days outrun the two starting freezes: no covered day is counted.
        val solved = listOf(5L, 4L, 0L).map(today::minusDays)
        assertEquals(1, streak(solved).current)
        assertEquals(2, streak(solved).best)
    }

    @Test
    fun `today with no solve yet is never a covered day`() {
        assertEquals(2, streak(listOf(yesterday, today.minusDays(2))).current)
    }

    @Test
    fun `only solved days earn a freeze, covered days do not advance the ladder`() {
        // Solved 1-3, covered 4, solved 5-7 is a 7-day streak but only 3 solved in a row: no freeze earned.
        val solved = listOf(6L, 5L, 4L, 2L, 1L, 0L).map(today::minusDays)
        val ledger = streak(solved)
        assertEquals(7, ledger.current)
        assertEquals(1, ledger.freezes.held)
        assertEquals(4, ledger.freezes.daysToNext)
    }

    @Test
    fun `a broken run still counts toward the best streak`() {
        // Last solved a week ago: no current streak, but the run is still the best one.
        val run = listOf(today.minusDays(7), today.minusDays(8))
        assertEquals(0, streak(run).current)
        assertEquals(2, streak(run).best)
    }
}
