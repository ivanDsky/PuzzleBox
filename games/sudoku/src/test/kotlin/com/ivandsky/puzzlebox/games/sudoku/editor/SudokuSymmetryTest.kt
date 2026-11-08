package com.ivandsky.puzzlebox.games.sudoku.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SudokuSymmetryTest {

    private fun draft(size: Int, vararg givens: Pair<Int, Int>): SudokuDraft {
        val cells = MutableList(size * size) { 0 }
        givens.forEach { (cell, digit) -> cells[cell] = digit }
        return SudokuDraft(size, cells)
    }

    @Test
    fun off_pairs_nothing() {
        assertNull(SudokuSymmetry.Off.mirrorOf(0, 6))
        assertNull(SudokuSymmetry.Off.mirrorOf(40, 9))
    }

    @Test
    fun rotational_is_the_half_turn_at_both_sizes() {
        assertEquals(35, SudokuSymmetry.Rotational.mirrorOf(0, 6))
        assertEquals(0, SudokuSymmetry.Rotational.mirrorOf(35, 6))
        assertEquals(80, SudokuSymmetry.Rotational.mirrorOf(0, 9))
        assertEquals(60, SudokuSymmetry.Rotational.mirrorOf(20, 9))
    }

    @Test
    fun rotational_centre_of_the_nine_board_is_its_own_mirror() {
        assertNull(SudokuSymmetry.Rotational.mirrorOf(40, 9))
    }

    @Test
    fun mirror_flips_columns_at_both_sizes() {
        assertEquals(5, SudokuSymmetry.Mirror.mirrorOf(0, 6))
        assertEquals(6, SudokuSymmetry.Mirror.mirrorOf(11, 6))
        assertEquals(8, SudokuSymmetry.Mirror.mirrorOf(0, 9))
        assertEquals(17, SudokuSymmetry.Mirror.mirrorOf(9, 9))
    }

    @Test
    fun mirror_middle_column_of_the_nine_board_is_its_own_mirror() {
        assertNull(SudokuSymmetry.Mirror.mirrorOf(4, 9))
        assertNull(SudokuSymmetry.Mirror.mirrorOf(40, 9))
    }

    @Test
    fun mirror_is_an_involution() {
        for (size in SudokuDraft.SIZES) for (symmetry in SudokuSymmetry.entries) for (cell in 0 until size * size) {
            val other = symmetry.mirrorOf(cell, size) ?: continue
            assertEquals(cell, symmetry.mirrorOf(other, size))
        }
    }

    @Test
    fun unpaired_is_empty_cells_whose_mirror_holds_a_given() {
        val board = draft(6, 0 to 3, 5 to 2, 7 to 1)
        assertEquals(emptySet<Int>(), SudokuSymmetry.Off.unpaired(board))
        assertEquals(setOf(28, 30, 35), SudokuSymmetry.Rotational.unpaired(board))
        assertEquals(setOf(10), SudokuSymmetry.Mirror.unpaired(board))
    }

    @Test
    fun a_given_with_a_given_mirror_is_paired() {
        val board = draft(6, 0 to 3, 35 to 2)
        assertEquals(emptySet<Int>(), SudokuSymmetry.Rotational.unpaired(board))
    }
}
