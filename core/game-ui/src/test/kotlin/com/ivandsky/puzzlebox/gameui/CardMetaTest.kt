package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardMetaTest {

    private val best = BestScore(timeMs = 252_000L, hints = 0)

    private fun model(order: RankOrder, number: Int? = 7, best: BestScore? = this.best) = GameCardModel(
        game = GameId("g"),
        name = "Game",
        brand = GameBrand.Neutral,
        status = TodayStatus.NotStarted,
        rankOrder = order,
        puzzleNumber = number,
        best = best,
    )

    @Test
    fun `hints-first model reads the best hints-first`() {
        val meta = cardMeta(model(RankOrder.HintsThenTime))!!
        assertTrue(meta.endsWith(bestScoreText(best, RankOrder.HintsThenTime)))
    }

    @Test
    fun `time-first model reads the best time-first`() {
        val meta = cardMeta(model(RankOrder.TimeThenHints))!!
        assertTrue(meta.endsWith(bestScoreText(best, RankOrder.TimeThenHints)))
        assertFalse(meta.contains(bestScoreText(best, RankOrder.HintsThenTime)))
    }

    @Test
    fun `number and best join, and either drops when absent`() {
        val both = cardMeta(model(RankOrder.TimeThenHints))!!
        val numberOnly = cardMeta(model(RankOrder.TimeThenHints, best = null))!!
        val bestOnly = cardMeta(model(RankOrder.TimeThenHints, number = null))!!
        assertTrue(numberOnly.contains("7"))
        assertFalse(bestOnly.contains("7"))
        assertEquals(both, numberOnly + both.removePrefix(numberOnly))
        assertTrue(both.endsWith(bestOnly))
    }

    @Test
    fun `no number and no best is no line`() {
        assertNull(cardMeta(model(RankOrder.TimeThenHints, number = null, best = null)))
    }
}
