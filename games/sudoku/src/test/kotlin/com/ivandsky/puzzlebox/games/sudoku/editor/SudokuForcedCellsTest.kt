package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuForcedCellsTest {

    private val miniSolution = KnownSolutions.grid(KnownSolutions.MINI_SAMPLE_SOLUTION)

    private fun miniWithBlanks(vararg blanks: Int): SudokuDraft =
        SudokuDraft(6, miniSolution.mapIndexed { idx, digit -> if (idx in blanks) 0 else digit })

    @Test
    fun `a naked single is returned with its cell and digit`() {
        assertEquals(mapOf(14 to miniSolution[14]), SudokuForcedCells.of(miniWithBlanks(14)))
    }

    @Test
    fun `a chain walks to the fixed point`() {
        val draft = SudokuDraft(6, KnownSolutions.grid(KnownSolutions.MINI_SAMPLE_GIVENS))
        val open = draft.givens.indices.filter { draft.givens[it] == 0 }
        assertEquals(open.associateWith { miniSolution[it] }, SudokuForcedCells.of(draft))
    }

    @Test
    fun `no given appears in the result`() {
        val draft = SudokuDraft(6, KnownSolutions.grid(KnownSolutions.MINI_SAMPLE_GIVENS))
        val given = draft.givens.indices.filter { draft.givens[it] != 0 }.toSet()
        assertTrue(SudokuForcedCells.of(draft).keys.none { it in given })
    }

    @Test
    fun `clashing givens return nothing`() {
        val givens = MutableList(36) { 0 }
        givens[0] = 3
        givens[5] = 3
        assertEquals(emptyMap<Int, Int>(), SudokuForcedCells.of(SudokuDraft(6, givens)))
    }

    @Test
    fun `a board that needs a guess stops short and holds no reveal-only cell`() {
        val guessOnly = intArrayOf(0, 5, 6, 11)
        val forced = SudokuForcedCells.of(miniWithBlanks(14, *guessOnly))
        assertEquals(mapOf(14 to miniSolution[14]), forced)
    }

    @Test
    fun `a board with no technique at all returns nothing`() {
        val givens = MutableList(36) { 0 }
        givens[0] = 1
        assertEquals(emptyMap<Int, Int>(), SudokuForcedCells.of(SudokuDraft(6, givens)))
    }

    @Test
    fun `a nine by nine draft walks too`() {
        val draft = SudokuDraft(9, KnownSolutions.grid(KnownSolutions.CLASSIC_SAMPLE_GIVENS))
        val solution = KnownSolutions.grid(KnownSolutions.CLASSIC_SAMPLE_SOLUTION)
        val open = draft.givens.indices.filter { draft.givens[it] == 0 }
        assertEquals(open.associateWith { solution[it] }, SudokuForcedCells.of(draft))
    }
}
