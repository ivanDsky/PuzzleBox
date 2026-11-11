package com.ivandsky.puzzlebox.gameui.board

import com.ivandsky.puzzlebox.game.RankOrder
import org.junit.Assert.assertEquals
import org.junit.Test

/** One combined rank: board order, ties share a place and go to the real row first, hidden times unranked and last, real rows untouched. */
class RankWithGhostsTest {
    private fun real(rank: Int, name: String, seconds: Int, hints: Int = 0, isSelf: Boolean = false) =
        DailyBoardRowModel(rank, name, seconds * 1000L, hints, isSelf, playerId = name)

    private fun ghost(name: String, seconds: Int?, hintFree: Boolean = true) =
        GhostRowModel("ref-$name", name, seconds?.let { it * 1000L }, hintFree)

    /** Each line as `Real(name)=place` or `Ghost(name)=place`, a hidden ghost's place being `-`. */
    private fun labels(lines: List<DailyBoardLine>) = lines.map {
        when (it) {
            is DailyBoardLine.Real -> "Real(${it.row.name})=${it.place}"
            is DailyBoardLine.Ghost -> "Ghost(${it.row.name})=${it.place ?: "-"}"
        }
    }

    @Test
    fun ghostsInterleaveByTimeUnderTimeThenHints() {
        val rows = listOf(real(1, "r60", 60), real(2, "r80", 80), real(3, "r100", 100))
        val ghosts = listOf(ghost("g70", 70), ghost("g90", 90))

        assertEquals(
            listOf("Real(r60)=1", "Ghost(g70)=2", "Real(r80)=3", "Ghost(g90)=4", "Real(r100)=5"),
            labels(rankWithGhosts(rows, ghosts, RankOrder.TimeThenHints)),
        )
    }

    @Test
    fun ghostsInterleaveByHintsUnderHintsThenTime() {
        val rows = listOf(real(1, "r0h", 100, hints = 0), real(2, "r1h", 50, hints = 1))
        val ghosts = listOf(ghost("hintFree200", 200, hintFree = true), ghost("hinted10", 10, hintFree = false))

        // The hint-free ghost ties the real row on hints and is slower, so it ranks behind it; the hinted ghost ties the second real row on hints and is faster.
        assertEquals(
            listOf("Real(r0h)=1", "Ghost(hintFree200)=2", "Ghost(hinted10)=3", "Real(r1h)=4"),
            labels(rankWithGhosts(rows, ghosts, RankOrder.HintsThenTime)),
        )
    }

    @Test
    fun twoFasterGhostsPutThePlayerThird() {
        val rows = listOf(real(1, "me", 60, isSelf = true))
        val ghosts = listOf(ghost("g40", 40), ghost("g50", 50))

        assertEquals(
            listOf("Ghost(g40)=1", "Ghost(g50)=2", "Real(me)=3"),
            labels(rankWithGhosts(rows, ghosts, RankOrder.TimeThenHints)),
        )
    }

    @Test
    fun equalScoresShareAPlaceAndTheRealRowComesFirst() {
        val rows = listOf(real(1, "r", 60), real(2, "slow", 90))
        val ghosts = listOf(ghost("gA", 60), ghost("gB", 60))

        assertEquals(
            listOf("Real(r)=1", "Ghost(gA)=1", "Ghost(gB)=1", "Real(slow)=4"),
            labels(rankWithGhosts(rows, ghosts, RankOrder.TimeThenHints)),
        )
    }

    @Test
    fun aGhostThatUsedHintsRanksBehindAHintFreeOneAtTheSameTime() {
        val ghosts = listOf(ghost("hinted", 60, hintFree = false), ghost("clean", 60, hintFree = true))

        assertEquals(
            listOf("Ghost(clean)=1", "Ghost(hinted)=2"),
            labels(rankWithGhosts(emptyList(), ghosts, RankOrder.TimeThenHints)),
        )
    }

    @Test
    fun hiddenTimesAreUnrankedAndLastInTheOrderGiven() {
        val rows = listOf(real(1, "r60", 60), real(2, "r100", 100))
        val ghosts = listOf(ghost("hiddenA", null), ghost("g150", 150), ghost("hiddenB", null))

        assertEquals(
            listOf("Real(r60)=1", "Real(r100)=2", "Ghost(g150)=3", "Ghost(hiddenA)=-", "Ghost(hiddenB)=-"),
            labels(rankWithGhosts(rows, ghosts, RankOrder.TimeThenHints)),
        )
    }

    @Test
    fun realRowsKeepTheirPuzzleBoxRank() {
        val rows = listOf(real(1, "r60", 60), real(2, "me", 80, isSelf = true), real(4, "r100", 100, hints = 2))
        val ghosts = listOf(ghost("g70", 70), ghost("g50", 50), ghost("g200", 200))

        val kept = rankWithGhosts(rows, ghosts, RankOrder.TimeThenHints)
            .filterIsInstance<DailyBoardLine.Real>()
            .map { it.row }

        assertEquals(rows, kept)
    }

    @Test
    fun noRowsGivesTheSortedGhostsAndHiddenOnesLast() {
        val ghosts = listOf(ghost("hidden", null), ghost("g90", 90), ghost("g30", 30))

        assertEquals(
            listOf("Ghost(g30)=1", "Ghost(g90)=2", "Ghost(hidden)=-"),
            labels(rankWithGhosts(emptyList(), ghosts, RankOrder.TimeThenHints)),
        )
    }

    @Test
    fun ghostsAreSortedHereToo() {
        val ghosts = listOf(ghost("slowA", 90), ghost("fast", 30), ghost("slowB", 90))

        assertEquals(
            listOf("Ghost(fast)=1", "Ghost(slowA)=2", "Ghost(slowB)=2"),
            labels(rankWithGhosts(emptyList(), ghosts, RankOrder.TimeThenHints)),
        )
    }

    @Test
    fun noGhostsGivesTheRowsWithTheirOwnRanksAsPlaces() {
        // Today's board: places are the PuzzleBox ranks as given, even where two rows tie.
        val rows = listOf(real(1, "r60", 60), real(2, "r60b", 60), real(3, "r80", 80))

        assertEquals(
            rows.map { DailyBoardLine.Real(it, it.rank) },
            rankWithGhosts(rows, emptyList(), RankOrder.HintsThenTime),
        )
    }
}
