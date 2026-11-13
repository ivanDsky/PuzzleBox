package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.domain.ResultRank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [resultRankChip]: a ranked player gets "#12 of 340" that taps through; anything else leaves the Today chip alone. */
class ResultRankChipTest {

    @Test
    fun `a ranked player's chip reads place over count and taps through`() {
        var taps = 0
        val chip = resultRankChip(ResultRank.Ranked(position = 12, count = 340, topPercent = 4)) { taps++ }!!
        assertEquals("#12", chip.value)
        assertEquals("of 340", chip.label)
        assertEquals("result.hero.chip.rank", chip.tag)
        chip.onClick!!()
        assertEquals(1, taps)
    }

    @Test
    fun `the PuzzleBox place stands even with LinkedIn connections folded in`() {
        val rank = ResultRank.Ranked(12, 340, 4, ResultRank.WithConnections(14, 350))
        assertEquals("#12", resultRankChip(rank) {}!!.value)
    }

    @Test
    fun `no chip while the rank is hidden or signed out`() {
        assertNull(resultRankChip(ResultRank.Hidden) {})
        assertNull(resultRankChip(ResultRank.SignedOut) {})
    }
}
