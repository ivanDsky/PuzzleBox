package com.ivandsky.puzzlebox.gameui.board

import com.ivandsky.puzzlebox.game.RankOrder
import org.junit.Assert.assertEquals
import org.junit.Test

/** The ghost interleave: board order, ties to the real row, hidden times last, real rows untouched. */
class MergeGhostsTest {
    private fun real(rank: Int, name: String, seconds: Int, hints: Int = 0, isSelf: Boolean = false) =
        DailyBoardRowModel(rank, name, seconds * 1000L, hints, isSelf)

    private fun ghost(name: String, seconds: Int?, hintFree: Boolean = true) =
        GhostRowModel(name, seconds?.let { it * 1000L }, hintFree)

    private fun labels(lines: List<DailyBoardLine>) = lines.map {
        when (it) {
            is DailyBoardLine.Real -> "Real(${it.row.name})"
            is DailyBoardLine.Ghost -> "Ghost(${it.row.name})"
        }
    }

    @Test
    fun ghostsInterleaveByTimeUnderTimeThenHints() {
        val rows = listOf(real(1, "r60", 60), real(2, "r80", 80), real(3, "r100", 100))
        val ghosts = listOf(ghost("g70", 70), ghost("g90", 90))

        assertEquals(
            listOf("Real(r60)", "Ghost(g70)", "Real(r80)", "Ghost(g90)", "Real(r100)"),
            labels(mergeGhosts(rows, ghosts, RankOrder.TimeThenHints)),
        )
    }

    @Test
    fun ghostsInterleaveByHintsUnderHintsThenTime() {
        val rows = listOf(real(1, "r0h", 100, hints = 0), real(2, "r1h", 50, hints = 1))
        val ghosts = listOf(ghost("hintFree200", 200, hintFree = true), ghost("hinted10", 10, hintFree = false))

        assertEquals(
            listOf("Real(r0h)", "Ghost(hintFree200)", "Ghost(hinted10)", "Real(r1h)"),
            labels(mergeGhosts(rows, ghosts, RankOrder.HintsThenTime)),
        )
    }

    @Test
    fun aTieGoesToTheRealRow() {
        val rows = listOf(real(1, "r", 60))
        val ghosts = listOf(ghost("g", 60))

        assertEquals(listOf("Real(r)", "Ghost(g)"), labels(mergeGhosts(rows, ghosts, RankOrder.TimeThenHints)))
    }

    @Test
    fun aGhostThatUsedHintsRanksBehindAHintFreeOneAtTheSameTime() {
        val ghosts = listOf(ghost("hinted", 60, hintFree = false), ghost("clean", 60, hintFree = true))

        assertEquals(
            listOf("Ghost(clean)", "Ghost(hinted)"),
            labels(mergeGhosts(emptyList(), ghosts, RankOrder.TimeThenHints)),
        )
    }

    @Test
    fun hiddenTimesGoLastInTheOrderGiven() {
        val rows = listOf(real(1, "r60", 60), real(2, "r100", 100))
        val ghosts = listOf(ghost("hiddenA", null), ghost("g150", 150), ghost("hiddenB", null))

        assertEquals(
            listOf("Real(r60)", "Real(r100)", "Ghost(g150)", "Ghost(hiddenA)", "Ghost(hiddenB)"),
            labels(mergeGhosts(rows, ghosts, RankOrder.TimeThenHints)),
        )
    }

    @Test
    fun realRanksAreNeverChanged() {
        val rows = listOf(real(1, "r60", 60), real(2, "me", 80, isSelf = true), real(4, "r100", 100, hints = 2))
        val ghosts = listOf(ghost("g70", 70), ghost("g50", 50), ghost("g200", 200))

        val kept = mergeGhosts(rows, ghosts, RankOrder.TimeThenHints)
            .filterIsInstance<DailyBoardLine.Real>()
            .map { it.row }

        assertEquals(rows, kept)
    }

    @Test
    fun ghostsAfterTheLastRealRowAreAppended() {
        val rows = listOf(real(1, "r60", 60))
        val ghosts = listOf(ghost("g90", 90))

        assertEquals(listOf("Real(r60)", "Ghost(g90)"), labels(mergeGhosts(rows, ghosts, RankOrder.TimeThenHints)))
    }

    @Test
    fun noRowsGivesTheSortedGhosts() {
        val ghosts = listOf(ghost("hidden", null), ghost("g90", 90), ghost("g30", 30))

        assertEquals(
            listOf("Ghost(g30)", "Ghost(g90)", "Ghost(hidden)"),
            labels(mergeGhosts(emptyList(), ghosts, RankOrder.TimeThenHints)),
        )
    }

    @Test
    fun noGhostsGivesTheRowsUnchanged() {
        val rows = listOf(real(1, "r60", 60), real(2, "r80", 80))

        assertEquals(rows.map(DailyBoardLine::Real), mergeGhosts(rows, emptyList(), RankOrder.HintsThenTime))
    }

    @Test
    fun ghostsAreSortedHereToo() {
        val ghosts = listOf(ghost("slowA", 90), ghost("fast", 30), ghost("slowB", 90))

        assertEquals(
            listOf("Ghost(fast)", "Ghost(slowA)", "Ghost(slowB)"),
            labels(mergeGhosts(emptyList(), ghosts, RankOrder.TimeThenHints)),
        )
    }
}
