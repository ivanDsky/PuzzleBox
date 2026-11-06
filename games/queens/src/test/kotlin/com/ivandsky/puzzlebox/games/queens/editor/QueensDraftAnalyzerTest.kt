package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Queens draft verdict (spec A2/A7): a blank board reads Incomplete, and a fully
 * painted one is classified by the exhaustive solver. On 4×4 only two queen placements
 * satisfy rows, columns and no-touch — A = {1, 7, 8, 14} and B = {2, 4, 11, 13} — so the
 * region map alone decides Unique (only A fits), Multiple (both fit) or None (neither).
 */
class QueensDraftAnalyzerTest {

    @Test
    fun `a new draft is one region, so it reads Incomplete on the region count`() {
        val draft = QueensEditor.newDraft(queensVariantId(4))

        val verdict = QueensDraftAnalyzer.analyze(draft, Budget.Unlimited)

        assertEquals(Verdict.Incomplete(IncompleteReason(QueensDraftAnalyzer.REGIONS, 1)), verdict)
    }

    @Test
    fun `unpainted cells read Incomplete with their count`() {
        val regions = UNIQUE.toMutableList().apply {
            this[0] = QueensDraft.UNPAINTED
            this[15] = QueensDraft.UNPAINTED
        }

        val verdict = QueensDraftAnalyzer.analyze(QueensDraft(4, regions), Budget.Unlimited)

        assertEquals(Verdict.Incomplete(IncompleteReason(QueensDraftAnalyzer.UNPAINTED, 2)), verdict)
    }

    @Test
    fun `a board only placement A fits is Unique with the queen column of each row`() {
        val verdict = QueensDraftAnalyzer.analyze(QueensDraft(4, UNIQUE), Budget.Unlimited)

        assertEquals(Solution(listOf(1, 7, 8, 14)), (verdict as Verdict.Unique).solution)
    }

    @Test
    fun `a board both placements fit is Multiple with the cells they differ on`() {
        val rows = List(16) { it / 4 }

        val verdict = QueensDraftAnalyzer.analyze(QueensDraft(4, rows), Budget.Unlimited)

        assertEquals(
            Verdict.Multiple(
                solutionA = Solution(listOf(1, 7, 8, 14)),
                solutionB = Solution(listOf(2, 4, 11, 13)),
                differingCells = setOf(1, 7, 8, 14, 2, 4, 11, 13),
            ),
            verdict,
        )
    }

    @Test
    fun `a board neither placement fits is None`() {
        val verdict = QueensDraftAnalyzer.analyze(QueensDraft(4, NONE), Budget.Unlimited)

        assertEquals(Verdict.None(emptySet()), verdict)
    }

    @Test
    fun `an exhausted budget on a complete board is Undetermined`() {
        val verdict = QueensDraftAnalyzer.analyze(QueensDraft(4, UNIQUE), Budget { true })

        assertEquals(Verdict.Undetermined, verdict)
    }

    private companion object {
        // R0 holds B's 2 and 4 together, so only A = {1, 7, 8, 14} has one queen per region.
        val UNIQUE = listOf(
            0, 0, 0, 1,
            0, 0, 1, 1,
            2, 2, 2, 3,
            2, 2, 3, 3,
        )

        // R0 holds A's 1 and 7 and B's 2 and 4: no placement has one queen per region.
        val NONE = listOf(
            0, 0, 0, 0,
            0, 1, 1, 0,
            2, 2, 2, 2,
            3, 3, 3, 3,
        )
    }
}
