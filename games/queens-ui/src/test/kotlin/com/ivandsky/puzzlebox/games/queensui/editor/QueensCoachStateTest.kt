package com.ivandsky.puzzlebox.games.queensui.editor

import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraftAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ticket 39: the coach line's state from draft, verdict and mode (flow C, round 6). */
class QueensCoachStateTest {

    private fun draft(crowns: Set<Int>) = QueensDraft(SIZE, List(SIZE * SIZE) { QueensDraft.UNPAINTED }, crowns)

    private fun of(crowns: Set<Int>, verdict: Verdict, mode: QueensEditorMode) =
        QueensCoachState.of(draft(crowns), verdict, mode)

    @Test
    fun `crown mode with no crowns asks for all N`() {
        assertEquals(QueensCoachState.PlaceCrowns(0, SIZE), of(emptySet(), INCOMPLETE, QueensEditorMode.Crown))
    }

    @Test
    fun `fewer than N crowns asks for the rest in either mode`() {
        assertEquals(QueensCoachState.PlaceCrowns(3, SIZE), of(THREE, INCOMPLETE, QueensEditorMode.Crown))
        assertEquals(QueensCoachState.PlaceCrowns(3, SIZE), of(THREE, INCOMPLETE, QueensEditorMode.Paint))
    }

    @Test
    fun `N crowns on an incomplete board asks for regions`() {
        assertEquals(QueensCoachState.PaintRegions, of(SEVEN, INCOMPLETE, QueensEditorMode.Crown))
        assertEquals(QueensCoachState.PaintRegions, of(SEVEN, INCOMPLETE, QueensEditorMode.Paint))
    }

    @Test
    fun `crowns with two answers is Ambiguous`() {
        assertEquals(QueensCoachState.Ambiguous, of(SEVEN, MULTIPLE, QueensEditorMode.Crown))
        assertEquals(QueensCoachState.Ambiguous, of(THREE, MULTIPLE, QueensEditorMode.Paint))
    }

    @Test
    fun `Unique shows with or without crowns in either mode`() {
        for (mode in QueensEditorMode.entries) {
            assertEquals(QueensCoachState.Unique, of(emptySet(), UNIQUE, mode))
            assertEquals(QueensCoachState.Unique, of(SEVEN, UNIQUE, mode))
        }
    }

    @Test
    fun `paint mode with no crowns stays quiet unless Unique`() {
        assertEquals(QueensCoachState.Hidden, of(emptySet(), INCOMPLETE, QueensEditorMode.Paint))
        assertEquals(QueensCoachState.Hidden, of(emptySet(), MULTIPLE, QueensEditorMode.Paint))
    }

    @Test
    fun `checking, undetermined and no solution are Hidden`() {
        for (mode in QueensEditorMode.entries) {
            for (crowns in listOf(emptySet(), THREE, SEVEN)) {
                for (verdict in listOf(Verdict.Checking, Verdict.Undetermined, Verdict.None(setOf(0, 1)))) {
                    assertEquals(QueensCoachState.Hidden, of(crowns, verdict, mode))
                }
            }
        }
    }

    @Test
    fun `while Checking the coach keeps the last settled verdict's state`() {
        assertEquals(QueensCoachState.Ambiguous, shown(SEVEN, MULTIPLE, settled = null))
        assertEquals(QueensCoachState.Ambiguous, shown(SEVEN, Verdict.Checking, settled = MULTIPLE))
        assertEquals(QueensCoachState.Unique, shown(SEVEN, Verdict.Checking, settled = UNIQUE))
        assertEquals(QueensCoachState.PaintRegions, shown(SEVEN, INCOMPLETE, settled = MULTIPLE))
    }

    @Test
    fun `the first analysis of an empty board shows step 1`() {
        assertEquals(QueensCoachState.PlaceCrowns(0, SIZE), shown(emptySet(), Verdict.Checking, settled = null))
        assertEquals(QueensCoachState.PlaceCrowns(3, SIZE), shown(THREE, Verdict.Checking, settled = null))
    }

    private fun shown(crowns: Set<Int>, verdict: Verdict, settled: Verdict?) =
        QueensCoachState.shown(draft(crowns), verdict, settled, QueensEditorMode.Crown)

    private companion object {
        const val SIZE = 7
        val THREE = setOf(0, 9, 18)
        val SEVEN = setOf(0, 9, 18, 27, 29, 38, 47)
        val INCOMPLETE = Verdict.Incomplete(IncompleteReason(QueensDraftAnalyzer.UNPAINTED, 42))
        val SOLUTION_A = Solution(listOf(0, 9, 18, 27, 29, 38, 47))
        val SOLUTION_B = Solution(listOf(1, 10, 19, 28, 30, 39, 48))
        val MULTIPLE = Verdict.Multiple(SOLUTION_A, SOLUTION_B, setOf(0, 1))
        val UNIQUE = Verdict.Unique(SOLUTION_A)
    }
}
