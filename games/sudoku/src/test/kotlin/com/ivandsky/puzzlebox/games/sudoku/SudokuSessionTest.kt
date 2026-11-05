package com.ivandsky.puzzlebox.games.sudoku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SudokuSessionTest {

    private val puzzle = KnownSolutions.peanut450
    private val now = Instant.parse("2026-11-04T18:00:00.123456Z")
    private val start = SudokuSession(ref = null, puzzle = puzzle)

    /** Open cells of #450, ascending; the first two (0 and 1) are row peers. */
    private val open = puzzle.givens.indices.filter { puzzle.givens[it] == 0 }

    private fun SudokuSession.play(command: SudokuCommand?, at: Instant = now): SudokuSession =
        withCommand(requireNotNull(command) { "move was a no-op" }, at)

    private fun solved(): SudokuSession = open.fold(start) { session, idx ->
        session.play(SudokuMoves.place(session.board, idx, puzzle.solution[idx]))
    }

    @Test
    fun `placing every remaining solution digit completes the session at millisecond precision`() {
        val session = solved()
        assertTrue(session.isComplete)
        assertEquals(Instant.ofEpochMilli(now.toEpochMilli()), session.completedAt)
        assertEquals(open.size, session.history.done.size)
    }

    @Test
    fun `the win is stamped once, by the Place that completes the board`() {
        val last = open.last()
        val almost = open.dropLast(1).fold(start) { session, idx ->
            session.play(SudokuMoves.place(session.board, idx, puzzle.solution[idx]))
        }
        assertFalse(almost.isComplete)
        val won = almost.play(SudokuMoves.place(almost.board, last, puzzle.solution[last]), now.plusSeconds(7))
        assertEquals(Instant.ofEpochMilli(now.plusSeconds(7).toEpochMilli()), won.completedAt)

        val reopened = won.board.with(last, SudokuCell.Empty)
        assertEquals(won, won.play(SudokuMoves.place(reopened, last, puzzle.solution[last]), now.plusSeconds(60)))
    }

    @Test
    fun `any full board without conflicts wins, even one that differs from the stored solution`() {
        val blank = SudokuPuzzle(SudokuVariants.MINI, List(36) { 0 }, puzzle.solution)
        val relabeled = puzzle.solution.map { if (it == 1) 2 else if (it == 2) 1 else it }
        val won = relabeled.indices.fold(SudokuSession(ref = null, puzzle = blank)) { session, idx ->
            assertFalse("complete before cell $idx", session.isComplete)
            session.play(SudokuMoves.place(session.board, idx, relabeled[idx]))
        }
        assertNotEquals(blank.solution, won.board.cells.map { it.digit })
        assertTrue(won.isComplete)
        assertTrue(SudokuGameRules.isComplete(SudokuState(blank, won.board)))
    }

    @Test
    fun `a full board with a conflict is not complete`() {
        val last = open.last()
        val almost = open.dropLast(1).fold(start) { session, idx ->
            session.play(SudokuMoves.place(session.board, idx, puzzle.solution[idx]))
        }
        val wrongDigit = (1..6).first { it != puzzle.solution[last] }
        val full = almost.play(SudokuMoves.place(almost.board, last, wrongDigit))
        assertTrue(full.board.isFull)
        assertFalse(full.isComplete)
        assertNull(full.completedAt)
        assertFalse(SudokuGameRules.isComplete(SudokuState(puzzle, full.board)))
    }

    @Test
    fun `undo and redo walk back and forth over every command kind`() {
        val a = open[0]
        val b = open[1]
        val noted = start.play(SudokuMoves.toggleNote(start.board, b, 3))
        val steps = listOf<(SudokuSession) -> SudokuCommand?>(
            { SudokuMoves.toggleNote(it.board, a, 3) },
            { SudokuMoves.place(it.board, a, 3) },
            { SudokuMoves.erase(it.board, a) },
            { SudokuMoves.reveal(it.board, a, puzzle.solution[a]) },
            { SudokuMoves.clear(it.board) },
        )
        var session = noted
        for (step in steps) {
            val command = requireNotNull(step(session))
            val after = session.play(command)
            val undone = after.withUndo()
            assertEquals("undo $command", session.board, undone.board)
            assertEquals(session.history.done, undone.history.done)
            assertEquals(listOf(command), undone.history.undone)
            assertTrue(undone.canRedo)
            val redone = undone.withRedo()
            assertEquals("redo $command", after, redone)
            session = redone
        }
        assertEquals(setOf(SudokuCommand.ToggleNote::class, SudokuCommand.Place::class, SudokuCommand.Erase::class,
            SudokuCommand.HintReveal::class, SudokuCommand.Clear::class), session.history.done.map { it::class }.toSet())

        val rewound = generateSequence(session) { it.takeIf { s -> s.canUndo }?.withUndo() }.last()
        assertEquals(start.board, rewound.board)
        assertFalse(rewound.canUndo)
        assertEquals(steps.size + 1, rewound.history.undone.size)
    }

    @Test
    fun `undo and redo with nothing to walk return the same session`() {
        assertFalse(start.canUndo)
        assertFalse(start.canRedo)
        assertSame(start, start.withUndo())
        assertSame(start, start.withRedo())
    }

    @Test
    fun `a new command after an undo forks the timeline`() {
        val placed = start.play(SudokuMoves.place(start.board, open[0], 4))
        val forked = placed.withUndo().play(SudokuMoves.place(start.board, open[0], 5))
        assertFalse(forked.canRedo)
        assertEquals(SudokuCell.Entry(5), forked.board.cells[open[0]])
    }

    @Test
    fun `a completed session is frozen against commands, undo, redo and hints`() {
        val session = solved()
        val board = session.board.with(open[0], SudokuCell.Empty)
        assertEquals(session, session.withCommand(SudokuMoves.place(board, open[0], 1)!!, now.plusSeconds(5)))
        assertFalse(session.canUndo)
        assertFalse(session.canRedo)
        assertSame(session, session.withUndo())
        assertSame(session, session.withRedo())
        assertSame(session, session.withHintUsed())
    }

    @Test
    fun `hints count outside the history, so undo never refunds one`() {
        val hinted = start.withHintUsed().withHintUsed()
        assertEquals(2, hinted.hintsUsed)
        assertFalse(hinted.canUndo)
        val placed = hinted.play(SudokuMoves.reveal(hinted.board, open[0], puzzle.solution[open[0]]))
        assertEquals(2, placed.withUndo().hintsUsed)
    }

    @Test
    fun `notes mode is a setting, not an undo unit`() {
        val placed = start.play(SudokuMoves.place(start.board, open[0], 4))
        val noting = placed.withNotesMode(true)
        assertTrue(noting.notesMode)
        assertEquals(placed.history, noting.history)
        assertTrue(noting.withUndo().notesMode)
        assertSame(noting, noting.withNotesMode(true))
        assertFalse(noting.withNotesMode(false).notesMode)
    }

    @Test
    fun `a session has started once a command is done or a hint is counted`() {
        assertFalse(start.hasStarted)
        assertTrue(start.withHintUsed().hasStarted)
        val placed = start.play(SudokuMoves.place(start.board, open[0], 4))
        assertTrue(placed.hasStarted)
        assertFalse(placed.withUndo().hasStarted)
        assertFalse(start.withNotesMode(true).hasStarted)
    }
}
