package com.ivandsky.puzzlebox.games.sudoku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SudokuCommandTest {

    private val fresh = SudokuBoard.of(KnownSolutions.peanut450)

    @Test
    fun `revert is the exact inverse of apply on every open cell, digit and notes layout`() {
        val noted = fresh.cells.indices
            .filter { fresh.cells[it] == SudokuCell.Empty }
            .fold(fresh) { board, idx -> board.with(idx, SudokuCell.Notes(((idx * 7) % 63) + 1)) }
        for (board in listOf(fresh, noted)) {
            for (idx in board.cells.indices) {
                for (digit in 1..6) {
                    val command = SudokuMoves.place(board, idx, digit) ?: continue
                    assertEquals(board, command.revert(command.apply(board)))
                }
            }
        }
    }

    @Test
    fun `a place command never targets a given`() {
        assertThrows(IllegalArgumentException::class.java) {
            SudokuCommand.Place(2, 1, SudokuCell.Given(3), emptyList())
        }
    }

    @Test
    fun `commands compare by value`() {
        assertEquals(SudokuMoves.place(fresh, 0, 4), SudokuMoves.place(fresh, 0, 4))
    }
}
