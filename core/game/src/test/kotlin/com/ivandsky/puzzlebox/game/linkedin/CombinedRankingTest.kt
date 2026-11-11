package com.ivandsky.puzzlebox.game.linkedin

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class CombinedRankingTest {
    private fun score(seconds: Int, hints: Int = 0) = BestScore(seconds * 1_000L, hints)

    private val timeFirst = RankOrder.TimeThenHints.comparator

    /** Each slot as `row0=1`, `ghost1=2`, a hidden ghost's place being `-`. */
    private fun labels(slots: List<RankedSlot>) = slots.map { "${if (it.ghost) "ghost" else "row"}${it.index}=${it.place ?: "-"}" }

    @Test
    fun `ghosts take places among the rows by their score`() {
        val slots = rankCombined(listOf(score(60), score(80), score(100)), listOf(score(90), score(70)), timeFirst)

        assertEquals(listOf("row0=1", "ghost1=2", "row1=3", "ghost0=4", "row2=5"), labels(slots))
    }

    @Test
    fun `equal scores share a place, the row first, and the next place skips past them`() {
        val slots = rankCombined(listOf(score(60), score(90)), listOf(score(60), score(60)), timeFirst)

        assertEquals(listOf("row0=1", "ghost0=1", "ghost1=1", "row1=4"), labels(slots))
    }

    @Test
    fun `a hinted ghost is behind a hint free one at the same time`() {
        val slots = rankCombined(emptyList(), listOf(score(60, hints = 1), score(60)), timeFirst)

        assertEquals(listOf("ghost1=1", "ghost0=2"), labels(slots))
    }

    @Test
    fun `hints come first under the hints-first order`() {
        val slots = rankCombined(listOf(score(100, hints = 0), score(50, hints = 1)), listOf(score(10, hints = 1)), RankOrder.HintsThenTime.comparator)

        assertEquals(listOf("row0=1", "ghost0=2", "row1=3"), labels(slots))
    }

    @Test
    fun `a hidden time is unranked, last, in the order given, and not counted`() {
        val slots = rankCombined(listOf(score(60)), listOf(null, score(30), null), timeFirst)

        assertEquals(listOf("ghost1=1", "row0=2", "ghost0=-", "ghost2=-"), labels(slots))
        assertEquals(2, slots.rankedCount())
    }

    @Test
    fun `rows given out of order are ranked by their score`() {
        val slots = rankCombined(listOf(score(90), score(60)), emptyList(), timeFirst)

        assertEquals(listOf("row1=1", "row0=2"), labels(slots))
    }

    @Test
    fun `an empty board has no slots`() {
        assertEquals(emptyList<RankedSlot>(), rankCombined(emptyList(), emptyList(), timeFirst))
        assertEquals(0, emptyList<RankedSlot>().rankedCount())
    }
}
