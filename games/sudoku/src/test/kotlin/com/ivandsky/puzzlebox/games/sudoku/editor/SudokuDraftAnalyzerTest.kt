package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import com.ivandsky.puzzlebox.games.sudoku.sudokuPlugin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuDraftAnalyzerTest {

    private val samples = listOf(KnownSolutions.miniSample, KnownSolutions.classicSample)

    private fun analyze(draft: SudokuDraft, budget: Budget = Budget.Unlimited): Verdict {
        val geometry = requireNotNull(SudokuVariants.bySize(draft.size)).geometry
        return SudokuDraftAnalyzer.analyze(draft, geometry, budget)
    }

    private fun SudokuPuzzle.draft(givens: List<Int> = this.givens) = SudokuDraft(geometry.size, givens)

    @Test
    fun `an empty board is incomplete with no givens at both sizes`() {
        for (size in SudokuDraft.SIZES) {
            val verdict = analyze(SudokuDraft(size, List(size * size) { 0 }))
            assertEquals(Verdict.Incomplete(IncompleteReason(SudokuDraftAnalyzer.EMPTY, 0)), verdict)
        }
    }

    @Test
    fun `a unique board carries its digit per cell at both sizes`() {
        for (puzzle in samples) {
            assertEquals(Verdict.Unique(Solution(puzzle.solution)), analyze(puzzle.draft()))
        }
    }

    @Test
    fun `an ambiguous board carries two solutions and exactly the cells they differ on at both sizes`() {
        val mini = List(36) { if (it == 0) 1 else 0 }
        val r3c3 = 2 * 9 + 2
        val classic = KnownSolutions.classicSample.givens.toMutableList().also { it[r3c3] = 0 }
        for (draft in listOf(SudokuDraft(6, mini), SudokuDraft(9, classic))) {
            val verdict = analyze(draft) as Verdict.Multiple
            val a = verdict.solutionA.values
            val b = verdict.solutionB.values
            val expected = a.indices.filter { a[it] != b[it] }.toSet()
            assertTrue(expected.isNotEmpty())
            assertEquals(expected, verdict.differingCells)
            draft.givens.forEachIndexed { idx, given -> if (given != 0) assertEquals(given, a[idx]) }
        }
    }

    @Test
    fun `clashing givens have no solution and no cells yet at both sizes`() {
        for (size in SudokuDraft.SIZES) {
            val clashInRow = List(size * size) { if (it == 0 || it == size - 1) 3 else 0 }
            assertEquals(Verdict.None(emptySet()), analyze(SudokuDraft(size, clashInRow)))
        }
    }

    @Test
    fun `a spent budget is undetermined`() {
        assertEquals(Verdict.Undetermined, analyze(KnownSolutions.classicSample.draft(), Budget { true }))
    }

    @Test
    fun `an editor reports its definition's game and variant`() {
        for (definition in listOf(SudokuGames.MINI, SudokuGames.CLASSIC)) {
            val editor = SudokuEditor(definition)
            assertEquals(definition.id, editor.gameId)
            assertEquals(listOf(definition.variant.id), editor.variants)
            val draft = editor.newDraft(definition.variant.id)
            assertEquals(definition.variant.id, editor.variantOf(draft))
            assertTrue(draft.givens.all { digit -> digit == 0 })
        }
    }

    @Test
    fun `a classic draft opened by the mini editor is a size mismatch`() {
        val classic = SudokuEditor(SudokuGames.CLASSIC)
        val bytes = classic.encodeDraft(DraftState(KnownSolutions.classicSample.draft(), CommandHistory()))
        val decoded = SudokuEditor(SudokuGames.MINI).decodeDraft(bytes) as DecodeResult.Err
        assertEquals(DecodeError.VariantSizeMismatch(SudokuVariants.MINI.id.value, 6, 9), decoded.error)
        assertTrue(classic.decodeDraft(bytes) is DecodeResult.Ok<*>)
    }

    @Test
    fun `the plugin still has no editor`() {
        assertNull(sudokuPlugin(SudokuGames.MINI).editor)
    }
}
