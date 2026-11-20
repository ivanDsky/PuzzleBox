package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuMoves
import com.ivandsky.puzzlebox.games.sudoku.notesMask
import org.junit.Assert.assertEquals
import org.junit.Test

class SudokuTrainerMoveTest {

    private val start = SudokuTrainerFixtures.starts.first { it.technique.value == "pointing" }
    private val board = start.board
    private val open = board.cells.indices.filter { board.digitAt(it) == 0 }

    private fun between(after: SudokuBoard) = SudokuTrainerMove.between(board, after)

    @Test
    fun `a digit placed is a Place, though it clears the peers' notes too`() {
        val idx = open.first { idx -> board.geometry.peersOf(idx).any { board.cells[it].notesMask and (1 shl (start.puzzle.solution[idx] - 1)) != 0 } }
        val digit = start.puzzle.solution[idx]
        val placed = SudokuMoves.place(board, idx, digit)!!.apply(board)

        assertEquals(SudokuTrainerMove.Place(idx, digit), between(placed))
    }

    @Test
    fun `a note that went is a RemoveNote and a note that came back is an AddNote`() {
        val idx = open.first { Integer.bitCount(board.cells[it].notesMask) > 1 }
        val digit = Integer.numberOfTrailingZeros(board.cells[idx].notesMask) + 1
        val without = SudokuMoves.toggleNote(board, idx, digit)!!.apply(board)

        assertEquals(SudokuTrainerMove.RemoveNote(idx, digit), between(without))
        assertEquals(SudokuTrainerMove.AddNote(idx, digit), SudokuTrainerMove.between(without, board))
    }

    @Test
    fun `an erase, a clear, a digit over a digit and a board that did not change are Other`() {
        val idx = open.first()
        val digit = start.puzzle.solution[idx]
        val placed = SudokuMoves.place(board, idx, digit)!!.apply(board)

        assertEquals(SudokuTrainerMove.Other, SudokuTrainerMove.between(placed, SudokuMoves.erase(placed, idx)!!.apply(placed)))
        assertEquals(SudokuTrainerMove.Other, between(SudokuMoves.clear(board)!!.apply(board)))
        assertEquals(SudokuTrainerMove.Other, SudokuTrainerMove.between(placed, SudokuMoves.place(placed, idx, digit % board.geometry.size + 1)!!.apply(placed)))
        assertEquals(SudokuTrainerMove.Other, between(board))
    }
}
