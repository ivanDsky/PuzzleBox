package com.ivandsky.puzzlebox.gameui.board

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import org.junit.Assert.assertEquals
import org.junit.Test

/** A LinkedIn time wins: the player's own import replaces the self row's time, a same-name connection replaces its row's. */
class RankWithLinkedInTest {
    private fun real(rank: Int, name: String, seconds: Int, hints: Int = 0, isSelf: Boolean = false) =
        DailyBoardRowModel(rank, name, seconds * 1000L, hints, isSelf)

    private fun ghost(name: String, seconds: Int?, hintFree: Boolean = true) =
        GhostRowModel("ref-$name", name, seconds?.let { it * 1000L }, hintFree)

    private fun own(seconds: Int, hints: Int = 0) = BestScore(seconds * 1000L, hints)

    /** Each line as `Real(name)=place@seconds` or `Ghost(name)=place@seconds`, a hidden ghost's place being `-`. */
    private fun labels(lines: List<DailyBoardLine>) = lines.map {
        when (it) {
            is DailyBoardLine.Real -> "Real(${it.row.name})=${it.place}@${it.row.timeMs / 1000}"
            is DailyBoardLine.Ghost -> "Ghost(${it.row.name})=${it.place ?: "-"}@${it.row.timeMs?.div(1000)}"
        }
    }

    private val rows = listOf(real(1, "a", 30), real(2, "b", 40), real(3, "me", 50, isSelf = true), real(4, "c", 60))

    @Test
    fun `the self row takes the imported time and its place moves with it`() {
        val lines = rankWithLinkedIn(rows, listOf(ghost("g", 45)), own(35), RankOrder.TimeThenHints)

        assertEquals(
            listOf("Real(a)=1@30", "Real(me)=2@35", "Real(b)=3@40", "Ghost(g)=4@45", "Real(c)=5@60"),
            labels(lines),
        )
    }

    @Test
    fun `the self row keeps its PuzzleBox rank whatever place the import gives it`() {
        val lines = rankWithLinkedIn(rows, emptyList(), own(35), RankOrder.TimeThenHints)

        assertEquals(3, lines.filterIsInstance<DailyBoardLine.Real>().single { it.row.isSelf }.row.rank)
        assertEquals(listOf("Real(a)=1@30", "Real(me)=2@35", "Real(b)=3@40", "Real(c)=4@60"), labels(lines))
    }

    @Test
    fun `a slower import moves the self row down, and its hints follow the import`() {
        val lines = rankWithLinkedIn(rows, emptyList(), own(55, hints = 1), RankOrder.HintsThenTime)

        assertEquals(listOf("Real(a)=1@30", "Real(b)=2@40", "Real(c)=3@60", "Real(me)=4@55"), labels(lines))
        assertEquals(1, lines.filterIsInstance<DailyBoardLine.Real>().last().row.hints)
    }

    @Test
    fun `with no import the self row keeps its PuzzleBox time`() {
        val lines = rankWithLinkedIn(rows, listOf(ghost("g", 45)), null, RankOrder.TimeThenHints)

        assertEquals(
            listOf("Real(a)=1@30", "Real(b)=2@40", "Ghost(g)=3@45", "Real(me)=4@50", "Real(c)=5@60"),
            labels(lines),
        )
    }

    @Test
    fun `a PuzzleBox row and a ghost of the same name are one row with the LinkedIn time`() {
        val lines = rankWithLinkedIn(rows, listOf(ghost(" C ", 20), ghost("other", 70)), null, RankOrder.TimeThenHints)

        assertEquals(
            listOf("Real(c)=1@20", "Real(a)=2@30", "Real(b)=3@40", "Real(me)=4@50", "Ghost(other)=5@70"),
            labels(lines),
        )
        assertEquals(4, lines.filterIsInstance<DailyBoardLine.Real>().single { it.row.name == "c" }.row.rank)
    }

    @Test
    fun `different names stay apart`() {
        val lines = rankWithLinkedIn(rows, listOf(ghost("cc", 20)), null, RankOrder.TimeThenHints)

        assertEquals(
            listOf("Ghost(cc)=1@20", "Real(a)=2@30", "Real(b)=3@40", "Real(me)=4@50", "Real(c)=5@60"),
            labels(lines),
        )
    }

    @Test
    fun `a same-name ghost with a hidden time keeps the PuzzleBox time and is not drawn`() {
        val lines = rankWithLinkedIn(rows, listOf(ghost("c", null)), null, RankOrder.TimeThenHints)

        assertEquals(listOf("Real(a)=1@30", "Real(b)=2@40", "Real(me)=3@50", "Real(c)=4@60"), labels(lines))
    }

    @Test
    fun `without an import or ghosts the rows are drawn as given`() {
        assertEquals(rows.map { DailyBoardLine.Real(it, it.rank) }, rankWithLinkedIn(rows, emptyList(), null, RankOrder.TimeThenHints))
    }
}
