package com.ivandsky.puzzlebox.gameui.board

import com.ivandsky.puzzlebox.game.RankOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What a row's score column says, per value and per board order: the copy of the 05 handoff, §7. */
class BoardRowValueTextTest {

    private fun text(value: BoardRowValue, order: RankOrder = RankOrder.TimeThenHints) = boardRowValueText(value, order)

    @Test
    fun `a timed value on a time-first board is the time alone`() {
        assertEquals("4:12", text(BoardRowValue.Timed(252_000L, hints = 0)))
        assertEquals("4:12", text(BoardRowValue.Timed(252_000L, hints = 3)))
    }

    @Test
    fun `a timed value on the Strands board leads with its hints`() {
        assertEquals("Perfect · 4:12", text(BoardRowValue.Timed(252_000L, hints = 0), RankOrder.HintsThenTime))
        assertEquals("1 hint · 3:05", text(BoardRowValue.Timed(185_000L, hints = 1), RankOrder.HintsThenTime))
        assertEquals("2 hints · 3:05", text(BoardRowValue.Timed(185_000L, hints = 2), RankOrder.HintsThenTime))
    }

    @Test
    fun `an hour-long time keeps its hours`() {
        assertEquals("1:02:03", text(BoardRowValue.Timed(3_723_000L, hints = 0)))
    }

    @Test
    fun `an average on a time-first board is avg and the time`() {
        assertEquals("avg 4:12", text(BoardRowValue.Average(252_000L, avgHints = 0.7)))
    }

    @Test
    fun `an average on the Strands board leads with its average hints to one decimal`() {
        assertEquals("0.7 hints · avg 3:05", text(BoardRowValue.Average(185_000L, avgHints = 0.7), RankOrder.HintsThenTime))
        assertEquals("0.7 hints · avg 3:05", text(BoardRowValue.Average(185_000L, avgHints = 2.0 / 3.0), RankOrder.HintsThenTime))
        assertEquals("0.0 hints · avg 3:05", text(BoardRowValue.Average(185_000L, avgHints = 0.0), RankOrder.HintsThenTime))
        assertEquals("1.5 hints · avg 3:05", text(BoardRowValue.Average(185_000L, avgHints = 1.5), RankOrder.HintsThenTime))
    }

    @Test
    fun `points read as whole points with a thousands separator`() {
        assertEquals("1,240 pts", text(BoardRowValue.Points(1240.0, games = 5, atLeast = false)))
        assertEquals("33 pts", text(BoardRowValue.Points(33.333, games = 1, atLeast = false)))
        assertEquals("0 pts", text(BoardRowValue.Points(0.0, games = 0, atLeast = false)))
        assertEquals("1,000,000 pts", text(BoardRowValue.Points(1_000_000.0, games = 9, atLeast = false)))
    }

    @Test
    fun `points round to the nearest whole point`() {
        assertEquals("1,240 pts", text(BoardRowValue.Points(1239.6, games = 5, atLeast = false)))
        assertEquals("1,239 pts", text(BoardRowValue.Points(1239.4, games = 5, atLeast = false)))
    }

    @Test
    fun `a lower-bound total carries the at-least sign`() {
        assertEquals("≥1,240 pts", text(BoardRowValue.Points(1240.0, games = 5, atLeast = true)))
    }

    @Test
    fun `points ignore the board order`() {
        val points = BoardRowValue.Points(1240.0, games = 5, atLeast = false)
        assertEquals(text(points, RankOrder.TimeThenHints), text(points, RankOrder.HintsThenTime))
    }

    @Test
    fun `only points name the games they count`() {
        assertEquals("5 games", boardRowValueDetail(BoardRowValue.Points(1240.0, games = 5, atLeast = false)))
        assertEquals("1 game", boardRowValueDetail(BoardRowValue.Points(100.0, games = 1, atLeast = false)))
        assertNull(boardRowValueDetail(BoardRowValue.Timed(252_000L, hints = 0)))
        assertNull(boardRowValueDetail(BoardRowValue.Average(252_000L, avgHints = 0.0)))
    }

    @Test
    fun `a row with no value of its own keeps its time and hints`() {
        val row = DailyBoardRowModel(1, "Maya", 62_000L, 2, isSelf = false, playerId = "maya")

        assertEquals(BoardRowValue.Timed(62_000L, 2), row.scoreValue)
        assertEquals(BoardRowValue.Timed(70_000L, 2), row.copy(timeMs = 70_000L).scoreValue)
    }

    @Test
    fun `a row with a value of its own shows that`() {
        val points = BoardRowValue.Points(1240.0, games = 5, atLeast = false)
        val row = DailyBoardRowModel(1, "Maya", 0L, 0, isSelf = false, playerId = "maya", value = points)

        assertEquals(points, row.scoreValue)
    }
}
