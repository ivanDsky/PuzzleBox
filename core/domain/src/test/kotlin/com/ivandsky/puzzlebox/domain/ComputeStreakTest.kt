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
        assertEquals(0, streak.ledger(emptyList()).current)
    }

    @Test
    fun `solved today and yesterday is two`() {
        assertEquals(2, streak.ledger(listOf(today, yesterday)).current)
    }

    @Test
    fun `solved yesterday only is one`() {
        assertEquals(1, streak.ledger(listOf(yesterday)).current)
    }

    @Test
    fun `last solve two days ago is no streak`() {
        assertEquals(0, streak.ledger(listOf(today.minusDays(2), today.minusDays(3))).current)
    }

    @Test
    fun `opened but unsolved days are ignored`() {
        // The caller passes only solved days; an opened-unsolved yesterday leaves a gap.
        val solved = listOf(today, today.minusDays(2))
        assertEquals(1, streak.ledger(solved).current)
    }

    @Test
    fun `duplicate days collapse`() {
        assertEquals(2, streak.ledger(listOf(today, today, yesterday, yesterday)).current)
    }

    @Test
    fun `no solves is no best streak`() {
        assertEquals(0, streak.ledger(emptyList()).best)
    }

    @Test
    fun `the best streak is the longest run, wherever it falls`() {
        val oldRun = (10L..13L).map { today.minusDays(it) }
        assertEquals(4, streak.ledger(oldRun + listOf(yesterday, today)).best)
    }

    @Test
    fun `the best streak ignores order and duplicates`() {
        assertEquals(3, streak.ledger(listOf(today, today.minusDays(2), yesterday, today, yesterday)).best)
    }

    @Test
    fun `a broken run still counts toward the best streak`() {
        // Last solved a week ago: no current streak, but the run is still the best one.
        val run = listOf(today.minusDays(7), today.minusDays(8))
        assertEquals(0, streak.ledger(run).current)
        assertEquals(2, streak.ledger(run).best)
    }
}
