package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft.Companion.UNPAINTED
import org.junit.Assert.assertEquals
import org.junit.Test

/** Fix-list rows for answer crowns (round 6): a region with 2+ crowns, a painted region with none. */
class QueensCrownIssueTest {

    @Test
    fun `a draft without crowns has no crown issues`() {
        assertEquals(emptyList<QueensCrownIssue>(), QueensCrownIssue.issuesOf(QueensDraft(5, REGIONS)))
    }

    @Test
    fun `a region holding two crowns is one DOUBLE row with that region's cells`() {
        // One crown per region, plus 8 next to 7 in region 1.
        val issues = QueensCrownIssue.issuesOf(QueensDraft(5, REGIONS, setOf(0, 7, 8, 14, 16, 23)))

        assertEquals(listOf(QueensCrownIssue(QueensCrownIssue.DOUBLE, 1, setOf(2, 3, 4, 7, 8))), issues)
    }

    @Test
    fun `a painted region holding no crown is one MISSING row`() {
        val issues = QueensCrownIssue.issuesOf(QueensDraft(5, REGIONS, setOf(0, 7, 14, 16)))

        assertEquals(listOf(QueensCrownIssue(QueensCrownIssue.MISSING, 4, setOf(17, 18, 21, 22, 23, 24))), issues)
    }

    @Test
    fun `unpainted cells never form a region, even under a crown`() {
        val regions = List(25) { if (it < 2) 0 else if (it == 4) 1 else UNPAINTED }

        val issues = QueensCrownIssue.issuesOf(QueensDraft(5, regions, setOf(4, 12, 20)))

        assertEquals(listOf(QueensCrownIssue(QueensCrownIssue.MISSING, 0, setOf(0, 1))), issues)
    }

    @Test
    fun `ids lists every row kind`() {
        assertEquals(setOf(QueensCrownIssue.DOUBLE, QueensCrownIssue.MISSING), QueensCrownIssue.ids)
    }

    private companion object {
        val REGIONS = listOf(
            0, 0, 1, 1, 1,
            0, 0, 1, 1, 2,
            0, 3, 3, 2, 2,
            3, 3, 4, 4, 2,
            3, 4, 4, 4, 4,
        )
    }
}
