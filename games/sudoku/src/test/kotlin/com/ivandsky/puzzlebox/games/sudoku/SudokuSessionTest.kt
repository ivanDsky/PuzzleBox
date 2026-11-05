package com.ivandsky.puzzlebox.games.sudoku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SudokuSessionTest {

    private val puzzle = KnownSolutions.peanut450
    private val now = Instant.parse("2026-11-04T18:00:00.123456Z")

    private fun solved(): SudokuSession = puzzle.givens.indices
        .filter { puzzle.givens[it] == 0 }
        .fold(SudokuSession(ref = null, puzzle = puzzle)) { session, idx ->
            session.withCommand(SudokuMoves.place(session.board, idx, puzzle.solution[idx])!!, now)
        }

    @Test
    fun `placing every remaining solution digit completes the session at millisecond precision`() {
        val session = solved()
        assertTrue(session.isComplete)
        assertEquals(Instant.ofEpochMilli(now.toEpochMilli()), session.completedAt)
        assertEquals(24, session.history.done.size)
    }

    @Test
    fun `a move short of the solution stays incomplete and is recorded`() {
        val start = SudokuSession(ref = null, puzzle = puzzle)
        val next = start.withCommand(SudokuMoves.place(start.board, 0, 4)!!, now)
        assertFalse(next.isComplete)
        assertNull(next.completedAt)
        assertEquals(SudokuCell.Entry(4), next.board.cells[0])
        assertEquals(1, next.history.done.size)
    }

    @Test
    fun `a full board with a wrong digit is not complete`() {
        val session = solved()
        val wrong = session.copy(completedAt = null, board = session.board.with(0, SudokuCell.Entry(6)))
        assertFalse(SudokuGameRules.isComplete(SudokuState(puzzle, wrong.board)))
    }

    @Test
    fun `a completed session is frozen`() {
        val session = solved()
        val board = session.board.with(0, SudokuCell.Empty)
        val command = SudokuMoves.place(board, 0, 1)!!
        assertEquals(session, session.withCommand(command, now.plusSeconds(5)))
    }
}
