package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.MarkKind
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.queens.QueensScoreModel
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.model.PuzzleKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The board state holder's command-undo and clock-hook behavior, no Compose needed. */
class QueensBoardStateTest {

    private val ref = QueensPuzzleRef(PuzzleKey.Daily(LocalDate.parse("2026-11-02"), 1))
    private fun fresh(settle: (QueensSession) -> QueensSession = { it }, onWin: (CompletionRecord) -> Unit = {}) =
        QueensPlayFixtures.boardState(
            session = QueensSession(ref = ref, puzzle = QueensUiFixtures.puzzle()),
            score = QueensScoreModel(),
            onWinRecord = onWin,
            settle = settle,
        )

    @Test
    fun `undo and redo walk the tap cycle one command at a time`() {
        val state = fresh()
        state.onTap(0) // MANUAL X
        state.onTap(0) // queen + AUTO fill
        assertTrue(Cell(0) in state.session.queens)

        state.undo()
        assertTrue(state.session.queens.isEmpty())
        assertEquals(MarkKind.MANUAL, state.session.markAt(Cell(0))?.kind)
        assertTrue(state.canRedo)

        state.redo()
        assertTrue(Cell(0) in state.session.queens)
        assertEquals(MarkKind.AUTO, state.session.markAt(Cell(1))?.kind)
        assertFalse(state.canRedo)
    }

    @Test
    fun `a new tap drops the redo stack`() {
        val state = fresh()
        state.onTap(0)
        state.undo()
        assertTrue(state.canRedo)
        state.onTap(5)
        assertFalse(state.canRedo)
    }

    @Test
    fun `a no-op action keeps the redo stack`() {
        val state = fresh()
        state.onTap(0)
        state.undo()
        state.clearBoard() // empty board: nothing to wipe, nothing to record
        assertTrue(state.canRedo)
    }

    @Test
    fun `undo and redo leave the hint count and banked time alone`() {
        val state = fresh()
        state.onTap(0)
        state.requestHint()
        state.bankElapsed(4_000)
        val hints = state.session.hintsUsed

        state.undo()
        state.redo()

        assertEquals(hints, state.session.hintsUsed)
        assertEquals(4_000L, state.session.elapsedMs)
    }

    @Test
    fun `settle sees every committed change and its time reaches the win record`() {
        var records = emptyList<CompletionRecord>()
        var changes = 0
        val state = fresh(
            settle = { next ->
                changes++
                if (next.isComplete) next.copy(elapsedMs = 90_000L) else next
            },
            onWin = { records = records + it },
        )

        QueensUiFixtures.solutionCells.forEach { state.onTap(it.idx); state.onTap(it.idx) }

        assertTrue(state.session.isComplete)
        assertEquals(90_000L, state.session.elapsedMs)
        assertEquals(1, records.size)
        assertEquals(90_000L, records.single().time)
        assertTrue("settle ran for the committed taps", changes >= 8)
    }

    @Test
    fun `settle is not consulted for a no-op action`() {
        var changes = 0
        val state = fresh(settle = { changes++; it })
        state.clearBoard()
        state.undo()
        assertEquals(0, changes)
    }

    @Test
    fun `clearing an empty board is a no-op - no undo entry, session unchanged`() {
        val state = fresh()
        val before = state.session
        state.clearBoard()
        assertEquals(before, state.session)
        assertFalse(state.canUndo)
    }

    @Test
    fun `undo is locked out once the board is solved`() {
        val state = fresh()
        QueensUiFixtures.solutionCells.forEach { state.onTap(it.idx); state.onTap(it.idx) }
        assertTrue(state.session.isComplete)
        assertFalse(state.canUndo)
        val solved = state.session
        state.undo()
        assertEquals(solved, state.session)
    }
}
