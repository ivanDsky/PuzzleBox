package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import org.junit.Assert.assertEquals
import org.junit.Test

class SudokuDraftClashTest {

    private fun verdict(size: Int, vararg cells: Int, budget: Budget = Budget.Unlimited): Verdict {
        val givens = MutableList(size * size) { 0 }
        cells.forEach { givens[it] = 3 }
        val geometry = requireNotNull(SudokuVariants.bySize(size)).geometry
        return SudokuDraftAnalyzer.analyze(SudokuDraft(size, givens), geometry, budget)
    }

    @Test
    fun `a row clash names exactly the two cells at both sizes`() {
        for (size in SudokuDraft.SIZES) {
            assertEquals(Verdict.None(setOf(0, size - 1)), verdict(size, 0, size - 1))
        }
    }

    @Test
    fun `a column clash names exactly the two cells at both sizes`() {
        for (size in SudokuDraft.SIZES) {
            assertEquals(Verdict.None(setOf(0, size * (size - 1))), verdict(size, 0, size * (size - 1)))
        }
    }

    @Test
    fun `a box clash names exactly the two cells at both sizes`() {
        for (size in SudokuDraft.SIZES) {
            val diagonal = size + 1
            assertEquals(Verdict.None(setOf(0, diagonal)), verdict(size, 0, diagonal))
        }
    }

    @Test
    fun `a clash is named without searching even on a spent budget`() {
        assertEquals(Verdict.None(setOf(0, 5)), verdict(6, 0, 5, budget = Budget { true }))
    }
}
