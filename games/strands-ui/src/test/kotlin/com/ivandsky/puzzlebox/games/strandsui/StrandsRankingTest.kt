package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.games.strands.strandsPlugin
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import org.junit.Assert.assertEquals
import org.junit.Test

/** SC13: a Strands best is fewer hints first, time only on equal hints (A25). */
class StrandsRankingTest {

    private val best = BestScore(timeMs = 60_000L, hints = 2)

    private fun verdict(timeMs: Long, hints: Int) =
        RecordVerdict.of(best, timeMs, hints, playAgain = false, order = strandsPlugin.score.rankOrder)

    @Test
    fun `fewer hints beat a faster time`() {
        assertEquals(RecordVerdict.NewBest, verdict(timeMs = 90_000L, hints = 1))
        assertEquals(RecordVerdict.NotBest, verdict(timeMs = 30_000L, hints = 3))
    }

    @Test
    fun `equal hints fall to time`() {
        assertEquals(RecordVerdict.NewBest, verdict(timeMs = 59_000L, hints = 2))
        assertEquals(RecordVerdict.NotBest, verdict(timeMs = 61_000L, hints = 2))
    }
}
