package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the answer crowns sit against the verdict (round 6), on one crafted 5×5 board with two
 * answers A and B; the verdicts are built by hand, since the fit never calls the solver.
 */
class QueensAnswerFitTest {

    private fun draft(crowns: Set<Int>) = QueensDraft(5, REGIONS, crowns)

    private val multiple = Verdict.Multiple(A, B, (A.values.toSet() - B.values.toSet()) + (B.values.toSet() - A.values.toSet()))

    @Test
    fun `no crowns or a verdict without solutions is Silent`() {
        assertEquals(QueensAnswerFit.Silent, QueensAnswerFit.of(draft(emptySet()), Verdict.Unique(A)))
        assertEquals(QueensAnswerFit.Silent, QueensAnswerFit.of(draft(emptySet()), multiple))
        for (verdict in listOf(Verdict.Incomplete(IncompleteReason(QueensDraftAnalyzer.UNPAINTED, 3)), Verdict.Checking, Verdict.Undetermined)) {
            assertEquals(QueensAnswerFit.Silent, QueensAnswerFit.of(draft(setOf(7)), verdict))
        }
    }

    @Test
    fun `crowns inside the unique solution are Kept`() {
        assertEquals(QueensAnswerFit.Kept, QueensAnswerFit.of(draft(setOf(7, 14)), Verdict.Unique(A)))
        assertEquals(QueensAnswerFit.Kept, QueensAnswerFit.of(draft(A.values.toSet()), Verdict.Unique(A)))
    }

    @Test
    fun `a crown off the unique solution, or no solution, is Broken`() {
        assertEquals(QueensAnswerFit.Broken, QueensAnswerFit.of(draft(setOf(7, 8)), Verdict.Unique(A)))
        assertEquals(QueensAnswerFit.Broken, QueensAnswerFit.of(draft(setOf(7)), Verdict.None(setOf(7))))
    }

    @Test
    fun `two crowns on B make B the author's answer and A's free queens the ghosts`() {
        val fit = QueensAnswerFit.of(draft(setOf(8, 11)), multiple) as QueensAnswerFit.Ambiguous

        assertEquals(false, fit.authorIsA)
        assertEquals(A.values.toSet() - setOf(8, 11), fit.ghosts)
        assertTrue(fit.ghosts.containsAll(fit.movable))
        // 7 touches region 3 below it and 16 touches region 4 to its right; 0, 14 and 23 sit inside their region.
        assertEquals(setOf(7, 16), fit.movable)
    }

    @Test
    fun `a tie picks A and the ghosts are B's free queens`() {
        val fit = QueensAnswerFit.of(draft(setOf(0)), multiple) as QueensAnswerFit.Ambiguous

        assertEquals(true, fit.authorIsA)
        assertEquals(setOf(8, 11, 19, 22), fit.ghosts)
    }

    @Test
    fun `ghostsOf an explicit witness is its queens minus the crowns`() {
        assertEquals(setOf(0, 19, 22), QueensAnswerFit.ghostsOf(draft(setOf(8, 11)), multiple, b = true))
        assertEquals(setOf(0, 7, 14, 16, 23), QueensAnswerFit.ghostsOf(draft(setOf(8, 11)), multiple, b = false))
    }

    private companion object {
        /**
         * ```
         * 0 0 1 1 1
         * 0 0 1 1 2
         * 0 3 3 2 2
         * 3 3 4 4 2
         * 3 4 4 4 4
         * ```
         */
        val REGIONS = listOf(
            0, 0, 1, 1, 1,
            0, 0, 1, 1, 2,
            0, 3, 3, 2, 2,
            3, 3, 4, 4, 2,
            3, 4, 4, 4, 4,
        )

        /** Columns 0 2 4 1 3: one queen per row, column and region of [REGIONS]. */
        val A = Solution(listOf(0, 7, 14, 16, 23))

        /** Columns 0 3 1 4 2: the second answer, sharing only cell 0 with [A]. */
        val B = Solution(listOf(0, 8, 11, 19, 22))
    }
}
