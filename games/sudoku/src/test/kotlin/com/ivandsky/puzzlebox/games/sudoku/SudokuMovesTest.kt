package com.ivandsky.puzzlebox.games.sudoku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SudokuMovesTest {

    private val puzzle = KnownSolutions.peanut450
    private val fresh = SudokuBoard.of(puzzle)

    @Test
    fun `place on an empty cell enters the digit`() {
        val command = SudokuMoves.place(fresh, 0, 4)!!
        assertEquals(SudokuCommand.Place(0, 4, SudokuCell.Empty, emptyList()), command)
        assertEquals(SudokuCell.Entry(4), command.apply(fresh).cells[0])
    }

    @Test
    fun `place keeps the cell's own notes hidden under the entry`() {
        val board = fresh.with(0, SudokuCell.Notes(0b1010))
        assertEquals(SudokuCell.Entry(4, 0b1010), SudokuMoves.place(board, 0, 4)!!.apply(board).cells[0])
    }

    @Test
    fun `place over an entry replaces the digit and keeps its notes`() {
        val board = fresh.with(0, SudokuCell.Entry(1, 0b100))
        assertEquals(SudokuCell.Entry(4, 0b100), SudokuMoves.place(board, 0, 4)!!.apply(board).cells[0])
    }

    @Test
    fun `place clears the digit from peer notes, visible and hidden, and nothing else`() {
        val bit4 = 1 shl 3
        val board = fresh
            .with(1, SudokuCell.Notes(bit4)) // row peer: becomes Empty
            .with(6, SudokuCell.Notes(bit4 or 1)) // box peer: keeps digit 1
            .with(30, SudokuCell.Entry(2, bit4)) // column peer: hidden note cleared
            .with(35, SudokuCell.Notes(bit4)) // not a peer of 0
        val command = SudokuMoves.place(board, 0, 4)!!
        assertEquals(listOf(1, 6, 30), command.clearedPeers)
        val after = command.apply(board)
        assertEquals(SudokuCell.Empty, after.cells[1])
        assertEquals(SudokuCell.Notes(1), after.cells[6])
        assertEquals(SudokuCell.Entry(2, 0), after.cells[30])
        assertEquals(SudokuCell.Notes(bit4), after.cells[35])
        assertEquals(board, command.revert(after))
    }

    @Test
    fun `place is a no-op on a given, on the same digit, and out of range`() {
        assertNull(SudokuMoves.place(fresh, 2, 1))
        val entered = fresh.with(0, SudokuCell.Entry(4))
        assertNull(SudokuMoves.place(entered, 0, 4))
        assertNull(SudokuMoves.place(fresh, 36, 1))
        assertNull(SudokuMoves.place(fresh, -1, 1))
        assertNull(SudokuMoves.place(fresh, 0, 0))
        assertNull(SudokuMoves.place(fresh, 0, 7))
    }
}
