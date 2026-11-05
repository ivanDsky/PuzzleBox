package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/** Solved-day streak (A8): consecutive solved puzzle days anchored at today or yesterday. */
class ComputeStreakTest {

    // 12:00 UTC on Nov 10 is 04:00 Nov 10 in Los Angeles: the puzzle day is Nov 10.
    private val clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC)
    private val registry = TestGames.registry(listOf(GameId("zip")))
    private val streak = ComputeStreak(GetPuzzleDay(registry, clock))

    private val today = LocalDate.of(2026, 11, 10)
    private val yesterday = today.minusDays(1)

    @Test
    fun `no solves is no streak`() {
        assertEquals(0, streak(emptyList()))
    }

    @Test
    fun `solved today and yesterday is two`() {
        assertEquals(2, streak(listOf(today, yesterday)))
    }

    @Test
    fun `solved yesterday only is one`() {
        assertEquals(1, streak(listOf(yesterday)))
    }

    @Test
    fun `last solve two days ago is no streak`() {
        assertEquals(0, streak(listOf(today.minusDays(2), today.minusDays(3))))
    }

    @Test
    fun `opened but unsolved days are ignored`() {
        // The caller passes only solved days; an opened-unsolved yesterday leaves a gap.
        val solved = listOf(today, today.minusDays(2))
        assertEquals(1, streak(solved))
    }

    @Test
    fun `duplicate days collapse`() {
        assertEquals(2, streak(listOf(today, today, yesterday, yesterday)))
    }
}
