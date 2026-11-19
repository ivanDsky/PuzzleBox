package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.moments.Moment
import com.ivandsky.puzzlebox.domain.moments.detectMoments
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The detector's input per counted solve, as it stood when the solve was made. */
class ReplaySolveFactsTest {

    private val fixture = RecapHistoryFixture()
    private val replay = ReplaySolveFacts(fixture.computeStreak, fixture.registry)

    private fun day(month: Int, dayOfMonth: Int) = LocalDate.of(2026, month, dayOfMonth)

    private suspend fun replayed() = replay(fixture.history())

    private suspend fun factsOf(game: com.ivandsky.puzzlebox.model.GameId, day: LocalDate) =
        replayed().single { it.game == game && it.day == day }.facts

    @Test
    fun `no history is no solves`() = runTest {
        assertEquals(emptyList<ReplayedSolve>(), replayed())
    }

    @Test
    fun `solves come oldest day first and only counted ones`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(11, 9)),
            fixture.solve(alpha, day(11, 2)),
            fixture.playAgain(alpha, day(11, 2)),
            fixture.solve(alpha, day(11, 4), puzzleNumber = null),
        )

        assertEquals(listOf(day(11, 2), day(11, 9)), replayed().map { it.day })
    }

    @Test
    fun `a game's first solve is no personal best, a faster one is, a slower one is not`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(11, 2), time = 400_000),
            fixture.solve(alpha, day(11, 4), time = 380_000),
            fixture.solve(alpha, day(11, 6), time = 390_000),
            fixture.solve(beta, day(11, 6), time = 500_000),
        )

        assertFalse(factsOf(alpha, day(11, 2)).newBest)
        assertTrue(factsOf(alpha, day(11, 4)).newBest)
        assertFalse(factsOf(alpha, day(11, 6)).newBest)
        // Another game's best is its own: beta's first solve is not measured against alpha's.
        assertFalse(factsOf(beta, day(11, 6)).newBest)
    }

    @Test
    fun `the best a solve beats is the best before it, across months`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(10, 5), time = 380_000),
            fixture.solve(alpha, day(11, 3), time = 390_000),
        )

        assertFalse(factsOf(alpha, day(11, 3)).newBest)
    }

    @Test
    fun `the best hint-free time before a solve ignores hinted solves and this one`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(11, 2), time = 100_000, hints = 1),
            fixture.solve(alpha, day(11, 4), time = 200_000),
            fixture.solve(alpha, day(11, 6), time = 150_000),
            fixture.solve(alpha, day(11, 8), time = 400_000),
        )

        assertNull(factsOf(alpha, day(11, 2)).bestHintFreeMs)
        assertNull(factsOf(alpha, day(11, 4)).bestHintFreeMs)
        assertEquals(200_000L, factsOf(alpha, day(11, 6)).bestHintFreeMs)
        assertEquals(150_000L, factsOf(alpha, day(11, 8)).bestHintFreeMs)
    }

    @Test
    fun `the last solve of a day's set finishes it, in the order they were completed`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(11, 2), time = 100_000, hints = 1).copy(firstCompletedAt = Instant.parse("2026-11-02T10:05:00Z")),
            fixture.solve(beta, day(11, 2), time = 200_000).copy(firstCompletedAt = Instant.parse("2026-11-02T10:00:00Z")),
        )

        val beta = factsOf(beta, day(11, 2))
        val alpha = factsOf(alpha, day(11, 2))

        assertEquals(1, beta.doneToday)
        assertEquals(2, alpha.doneToday)
        assertEquals(2, alpha.totalToday)
        assertEquals(Moment.FullSetDone.DayTotal(timeMs = 300_000, hints = 1), alpha.dayTotal)
        assertTrue(detectMoments(alpha).any { it is Moment.FullSetDone })
        assertFalse(detectMoments(beta).any { it is Moment.FullSetDone })
    }

    @Test
    fun `solves completed at the same instant, as imports are, finish the set in game order`() = runTest {
        fixture.store(fixture.imported(beta, day(11, 2)), fixture.imported(alpha, day(11, 2)))

        assertEquals(1, factsOf(alpha, day(11, 2)).doneToday)
        assertEquals(2, factsOf(beta, day(11, 2)).doneToday)
    }

    @Test
    fun `a day's first solve moves the streak, the next solves of the day do not`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(11, 1)).copy(firstCompletedAt = Instant.parse("2026-11-01T08:00:00Z")),
            fixture.solve(beta, day(11, 1)).copy(firstCompletedAt = Instant.parse("2026-11-01T09:00:00Z")),
            fixture.solve(alpha, day(11, 2)),
        )

        assertEquals(0 to 1, factsOf(alpha, day(11, 1)).let { it.streakBefore to it.streak })
        assertEquals(1 to 1, factsOf(beta, day(11, 1)).let { it.streakBefore to it.streak })
        assertEquals(1 to 2, factsOf(alpha, day(11, 2)).let { it.streakBefore to it.streak })
    }

    @Test
    fun `the streak reaches the 7 day mark on the day it does, across months`() = runTest {
        fixture.store(*(26..31).map { fixture.solve(alpha, day(10, it), puzzleNumber = 100 + it) }.toTypedArray(), fixture.solve(alpha, day(11, 1)))

        val seventh = factsOf(alpha, day(11, 1))

        assertEquals(6 to 7, seventh.streakBefore to seventh.streak)
        assertTrue(Moment.Streak(7) in detectMoments(seventh))
        assertTrue(detectMoments(factsOf(alpha, day(10, 31))).none { it is Moment.Streak })
    }

    @Test
    fun `a frozen day is part of the streak the next solve reaches`() = runTest {
        fixture.store(fixture.solve(alpha, day(11, 1)), fixture.solve(alpha, day(11, 3)))

        val third = factsOf(alpha, day(11, 3))

        assertEquals(2 to 3, third.streakBefore to third.streak)
    }

    @Test
    fun `an archive first solve is a counted solve and earns what Result gives it, a personal best included`() = runTest {
        fixture.store(fixture.solve(alpha, day(11, 2), time = 390_000), fixture.solve(alpha, day(11, 4), time = 380_000))
        fixture.captureArchived(alpha, day(11, 4))

        val archive = factsOf(alpha, day(11, 4))

        assertFalse(archive.playAgain)
        assertTrue(archive.newBest)
        assertEquals(listOf<Moment>(Moment.PersonalBest), detectMoments(archive))
        // The walk and the solved count agree: both rows are counted solves.
        assertEquals(2, replayed().size)
        assertEquals(2, fixture.computePeriodFacts(fixture.history(), RecapPeriod.Month(java.time.YearMonth.of(2026, 11))).solved)
    }

    @Test
    fun `an archive solve sets a time to beat like any other`() = runTest {
        fixture.store(fixture.solve(alpha, day(11, 2), time = 390_000), fixture.solve(alpha, day(11, 4), time = 380_000), fixture.solve(alpha, day(11, 6), time = 385_000))
        fixture.captureArchived(alpha, day(11, 4))

        assertFalse(factsOf(alpha, day(11, 6)).newBest)
    }

    @Test
    fun `an archive day earns no streak moment unless it joins runs the way Result reads it`() = runTest {
        // Seven days Nov 14..20 held by alpha, and an archive solve of beta on Nov 9: it joins nothing.
        fixture.store(
            *(14..20).map { fixture.solve(alpha, day(11, it), puzzleNumber = 100 + it) }.toTypedArray(),
            fixture.solve(beta, day(11, 9)),
        )
        fixture.captureArchived(beta, day(11, 9))

        val archive = factsOf(beta, day(11, 9))

        assertEquals(archive.streakBefore, archive.streak)
        assertTrue(detectMoments(archive).none { it is Moment.Streak })
    }

    @Test
    fun `friends are absent from every solve`() = runTest {
        fixture.store(fixture.solve(alpha, day(11, 2)), fixture.imported(beta, day(11, 3)))

        assertTrue(replayed().all { it.facts.friends == null && !it.facts.practice })
    }
}
