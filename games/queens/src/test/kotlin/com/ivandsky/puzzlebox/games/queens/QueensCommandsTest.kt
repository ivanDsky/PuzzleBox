package com.ivandsky.puzzlebox.games.queens

import org.junit.Assert.assertEquals
import org.junit.Test

/** revert(apply(s)) == s for every command kind over representative boards. */
class QueensCommandsTest {

    private fun c(vararg idx: Int) = idx.map(::Cell)

    private val boards = listOf(
        QueensBoard(),
        QueensBoard(queens = c(5)),
        QueensBoard(queens = c(9, 2, 14), manual = setOf(Cell(0), Cell(9))), // manual mark under a queen
        QueensBoard(manual = setOf(Cell(3), Cell(4))),
    )

    @Test
    fun `place queen reverts to the exact board`() {
        for (board in boards) {
            val cmd = QueensCommand.PlaceQueen(Cell(11))
            assertEquals(board, cmd.revert(cmd.apply(board)))
        }
    }

    @Test
    fun `toggle cross reverts to the exact board for both prior states`() {
        for (board in boards) {
            for (cell in listOf(Cell(3), Cell(12))) {
                val cmd = QueensCommand.ToggleCross(cell, wasMarked = cell in board.manual)
                assertEquals(board, cmd.revert(cmd.apply(board)))
            }
        }
    }

    @Test
    fun `remove queen restores its list position and the mark hidden under it`() {
        val board = boards[2]
        for ((index, cell) in board.queens.withIndex()) {
            val cmd = QueensCommand.RemoveQueen(cell, index, hadMark = cell in board.manual)
            val removed = cmd.apply(board)
            assertEquals(board.queens - cell, removed.queens)
            assertEquals(board.manual - cell, removed.manual)
            assertEquals("queen $cell", board, cmd.revert(removed))
        }
    }

    @Test
    fun `clear reverts to the queens and manual marks it wiped`() {
        for (board in boards) {
            val cmd = QueensCommand.Clear(board.queens, board.manual)
            assertEquals(QueensBoard(), cmd.apply(board))
            assertEquals(board, cmd.revert(cmd.apply(board)))
        }
    }

    @Test
    fun `a mismatched board never throws`() {
        // a stale save may re-apply a command against a board that diverged
        val board = QueensBoard(queens = c(1))
        QueensCommand.RemoveQueen(Cell(7), index = 99, hadMark = true).revert(board)
        QueensCommand.PlaceQueen(Cell(40)).revert(board)
        QueensCommand.ToggleCross(Cell(2), wasMarked = true).apply(board)
    }
}
