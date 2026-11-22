package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import org.junit.Assert.assertEquals
import org.junit.Test

class SudokuHintCaptionsTest {

    private val mini = SudokuPuzzle(
        SudokuVariants.MINI,
        List(36) { 0 },
        "612345345612456123123456561234234561".map { it.digitToInt() },
    )

    @Test
    fun `a clash in a box says this box, since no box is numbered on screen`() {
        // Cells 0 and 7 share box 0 (rows 1-2, columns 1-3) but no row or column.
        val board = SudokuBoard.of(mini).with(0, SudokuCell.Entry(3)).with(7, SudokuCell.Entry(3))

        assertEquals("Two 3s in this box", SudokuHintCaptions.violation(board))
    }

    @Test
    fun `a clash in a row or column names it in full`() {
        val row = SudokuBoard.of(mini).with(0, SudokuCell.Entry(3)).with(5, SudokuCell.Entry(3))
        val column = SudokuBoard.of(mini).with(0, SudokuCell.Entry(3)).with(18, SudokuCell.Entry(3))

        assertEquals("Two 3s in row 1", SudokuHintCaptions.violation(row))
        assertEquals("Two 3s in column 1", SudokuHintCaptions.violation(column))
    }
}
