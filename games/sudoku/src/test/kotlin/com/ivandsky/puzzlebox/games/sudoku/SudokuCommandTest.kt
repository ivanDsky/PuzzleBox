package com.ivandsky.puzzlebox.games.sudoku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SudokuCommandTest {

    // Peanut #450: idx 2 and 4 are givens (3, 5); idx 0 is open with solution 4.
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
        assertEquals(SudokuMoves.clear(fresh.with(0, SudokuCell.Entry(4))), SudokuMoves.clear(fresh.with(0, SudokuCell.Entry(4))))
    }

    @Test
    fun `erase drops an entry back to its hidden notes`() {
        val board = fresh.with(0, SudokuCell.Entry(4, 0b1010))
        val command = SudokuMoves.erase(board, 0)!!
        assertEquals(SudokuCommand.Erase(0, SudokuCell.Entry(4, 0b1010)), command)
        assertEquals(SudokuCell.Notes(0b1010), command.apply(board).cells[0])
    }

    @Test
    fun `erase empties an entry without notes and a notes cell`() {
        val entered = fresh.with(0, SudokuCell.Entry(4))
        assertEquals(SudokuCell.Empty, SudokuMoves.erase(entered, 0)!!.apply(entered).cells[0])
        val noted = fresh.with(0, SudokuCell.Notes(0b11))
        assertEquals(SudokuCell.Empty, SudokuMoves.erase(noted, 0)!!.apply(noted).cells[0])
    }

    @Test
    fun `erase is a no-op on empty, given and out-of-range cells`() {
        assertNull(SudokuMoves.erase(fresh, 0))
        assertNull(SudokuMoves.erase(fresh, 2))
        assertNull(SudokuMoves.erase(fresh, 36))
        assertNull(SudokuMoves.erase(fresh, -1))
        assertThrows(IllegalArgumentException::class.java) { SudokuCommand.Erase(0, SudokuCell.Empty) }
    }

    @Test
    fun `toggle note adds and removes one bit, and the last bit off leaves the cell empty`() {
        val once = SudokuMoves.toggleNote(fresh, 0, 3)!!.apply(fresh)
        assertEquals(SudokuCell.Notes(0b100), once.cells[0])
        val twice = SudokuMoves.toggleNote(once, 0, 5)!!.apply(once)
        assertEquals(SudokuCell.Notes(0b10100), twice.cells[0])
        val back = SudokuMoves.toggleNote(twice, 0, 3)!!.apply(twice)
        assertEquals(SudokuCell.Notes(0b10000), back.cells[0])
        assertEquals(SudokuCell.Empty, SudokuMoves.toggleNote(back, 0, 5)!!.apply(back).cells[0])
    }

    @Test
    fun `toggle note is a no-op on given and entry cells and out of range`() {
        assertNull(SudokuMoves.toggleNote(fresh, 2, 1))
        assertNull(SudokuMoves.toggleNote(fresh.with(0, SudokuCell.Entry(4)), 0, 1))
        assertNull(SudokuMoves.toggleNote(fresh, 0, 0))
        assertNull(SudokuMoves.toggleNote(fresh, 0, 7))
        assertNull(SudokuMoves.toggleNote(fresh, 36, 1))
    }

    @Test
    fun `clear empties every entry and notes cell in one undo unit and leaves givens`() {
        val board = fresh
            .with(0, SudokuCell.Entry(4, 0b1))
            .with(1, SudokuCell.Notes(0b110))
            .with(3, SudokuCell.Entry(2))
        val command = SudokuMoves.clear(board)!!
        assertEquals(
            mapOf(0 to SudokuCell.Entry(4, 0b1), 1 to SudokuCell.Notes(0b110), 3 to SudokuCell.Entry(2)),
            command.before,
        )
        assertEquals(fresh, command.apply(board))
        assertEquals(board, command.revert(command.apply(board)))
    }

    @Test
    fun `clear is a no-op when only givens and empty cells remain`() {
        assertNull(SudokuMoves.clear(fresh))
        assertThrows(IllegalArgumentException::class.java) { SudokuCommand.Clear(mapOf(2 to SudokuCell.Given(3))) }
    }

    @Test
    fun `reveal overwrites a wrong entry and keeps its hidden notes`() {
        val board = fresh.with(0, SudokuCell.Entry(1, 0b100))
        val command = SudokuMoves.reveal(board, 0, 4)!!
        assertEquals(SudokuCommand.HintReveal(0, 4, SudokuCell.Entry(1, 0b100), emptyList()), command)
        assertEquals(SudokuCell.Entry(4, 0b100), command.apply(board).cells[0])
    }

    @Test
    fun `reveal clears peer notes exactly like place`() {
        val bit4 = 0b1000
        val board = fresh
            .with(0, SudokuCell.Notes(0b11))
            .with(1, SudokuCell.Notes(bit4))
            .with(6, SudokuCell.Notes(bit4 or 0b1))
            .with(30, SudokuCell.Entry(2, bit4))
            .with(35, SudokuCell.Notes(bit4))
        val place = SudokuMoves.place(board, 0, 4)!!
        val reveal = SudokuMoves.reveal(board, 0, 4)!!
        assertEquals(place.clearedPeers, reveal.clearedPeers)
        assertEquals(place.apply(board), reveal.apply(board))
        assertEquals(board, reveal.revert(reveal.apply(board)))
    }

    @Test
    fun `reveal is a no-op on a given, on the digit already held, and out of range`() {
        assertNull(SudokuMoves.reveal(fresh, 2, 1))
        assertNull(SudokuMoves.reveal(fresh.with(0, SudokuCell.Entry(4)), 0, 4))
        assertNull(SudokuMoves.reveal(fresh, 36, 1))
        assertNull(SudokuMoves.reveal(fresh, 0, 7))
        assertThrows(IllegalArgumentException::class.java) {
            SudokuCommand.HintReveal(2, 1, SudokuCell.Given(3), emptyList())
        }
    }
}
