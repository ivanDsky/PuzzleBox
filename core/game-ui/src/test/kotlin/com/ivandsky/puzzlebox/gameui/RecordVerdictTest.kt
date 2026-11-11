package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.testing.repository.RankOrderCases
import com.ivandsky.puzzlebox.testing.repository.RankOrderCases.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec A10: "New best" only on a strictly lower SHOWN time of a first solve; replays never
 * rank. A26: a hints-first game ranks fewer hints first, equal hints falling to time.
 */
class RecordVerdictTest {

    private fun timeFirst(previousBestMs: Long?, timeMs: Long, playAgain: Boolean) = RecordVerdict.of(
        previous = previousBestMs?.let { BestScore(it, hints = 0) },
        timeMs = timeMs,
        hints = 0,
        playAgain = playAgain,
        order = RankOrder.TimeThenHints,
    )

    @Test
    fun `no previous best is the first solve`() {
        assertEquals(RecordVerdict.FirstSolve, timeFirst(previousBestMs = null, timeMs = 95_000, playAgain = false))
    }

    @Test
    fun `a strictly lower time is a new best`() {
        assertEquals(RecordVerdict.NewBest, timeFirst(previousBestMs = 95_000, timeMs = 94_000, playAgain = false))
    }

    @Test
    fun `an equal time ties the best`() {
        assertEquals(RecordVerdict.TiedBest, timeFirst(previousBestMs = 95_000, timeMs = 95_000, playAgain = false))
    }

    @Test
    fun `a few milliseconds faster that shows the same time is a tie, not a new best`() {
        // Device case: 3.826 s after a 3.862 s best — both read 00:03 on the result.
        assertEquals(RecordVerdict.TiedBest, timeFirst(previousBestMs = 3_862, timeMs = 3_826, playAgain = false))
    }

    @Test
    fun `a higher time is not a new best`() {
        assertEquals(RecordVerdict.NotBest, timeFirst(previousBestMs = 95_000, timeMs = 120_000, playAgain = false))
    }

    @Test
    fun `a replay is a replay however fast, and with no best on record`() {
        assertEquals(RecordVerdict.PlayAgain, timeFirst(previousBestMs = 95_000, timeMs = 3_000, playAgain = true))
        assertEquals(RecordVerdict.PlayAgain, timeFirst(previousBestMs = null, timeMs = 3_000, playAgain = true))
    }

    @Test
    fun `every rank-order contract case gets its verdict`() {
        for (case in RankOrderCases.all) {
            val expected = when (case.outcome) {
                Outcome.NewBest -> RecordVerdict.NewBest
                Outcome.TiedBest -> RecordVerdict.TiedBest
                Outcome.NotBest -> RecordVerdict.NotBest
                Outcome.PlayAgain -> RecordVerdict.PlayAgain
            }
            val actual = RecordVerdict.of(case.previous, case.play.timeMs, case.play.hints, case.playAgain, case.order)
            assertEquals(case.name, expected, actual)
        }
    }

    @Test
    fun `hints-first ranks fewer hints over a faster time`() {
        val previous = BestScore(timeMs = 60_000, hints = 2)
        val of = { timeMs: Long, hints: Int -> RecordVerdict.of(previous, timeMs, hints, playAgain = false, order = RankOrder.HintsThenTime) }
        assertEquals("fewer hints, slower", RecordVerdict.NewBest, of(90_000, 0))
        assertEquals("more hints, faster", RecordVerdict.NotBest, of(30_000, 3))
        assertEquals("equal hints falls to time", RecordVerdict.NewBest, of(59_000, 2))
        assertEquals("equal hints, same second", RecordVerdict.TiedBest, of(60_400, 2))
    }
}
