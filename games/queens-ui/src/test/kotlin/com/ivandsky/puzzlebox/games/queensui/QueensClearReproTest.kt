package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F-3 repro (fix round 3): "queens no longer visible after pressing Clear". The
 * suspected state bug — a queen placed AFTER clear not showing in the renderer model —
 * is exercised here against the real session + model pipeline, no Compose.
 *
 * Fix round 6 REVERSES test 2's round-3 expectation per the 2026-11-02 ruling: Clear
 * now wipes EVERYTHING — queens, AUTO marks AND MANUAL marks — as ONE undo unit (one
 * undo restores the exact pre-clear board, MANUAL marks included).
 */
class QueensClearReproTest {

    @Test
    fun `repro - queen placed after clear shows QUEEN in the renderer model`() {
        val placed = QueensUiFixtures.session().withQueenPlaced(Cell(1), QueensUiFixtures.T0)
        val state = QueensBoardState(initialSession = placed)
        assertEquals(listOf(Cell(1)), state.session.queens)

        state.clearBoard()
        assertEquals("clear removes every queen", 0, state.session.queens.size)

        state.onTap(8) // empty → MANUAL X
        state.onTap(8) // X → queen at (2,0)
        val model = QueensBoardModel.of(state.session, state.hint, state.hintPointed)
        assertEquals(QueensCellVisual.QUEEN, model.visuals[8])
        assertEquals(listOf(8), model.queens)
    }

    /** Round-6 ruling: Clear wipes MANUAL marks too — the board collapses fully; one
     *  undo restores the whole pre-clear picture (queens + AUTO + MANUAL). */
    @Test
    fun `clear wipes everything - one undo restores the pre-clear board`() {
        var session: QueensSession = QueensUiFixtures.session().withCrossToggled(Cell(15), QueensUiFixtures.T0)
        session = session.withQueenPlaced(Cell(1), QueensUiFixtures.T0)
        val state = QueensBoardState(initialSession = session)

        val before = QueensBoardModel.of(state.session)
        assertEquals(1, before.queens.size)
        assertTrue("pre-clear board is dense with AUTO marks", before.visuals.count { it == QueensCellVisual.X_AUTO } >= 4)
        assertEquals(QueensCellVisual.X_MANUAL, before.visuals[15])

        state.clearBoard()
        val after = QueensBoardModel.of(state.session)
        assertTrue(after.queens.isEmpty())
        assertEquals(
            "the board visually collapses: not one AUTO mark survives",
            0,
            after.visuals.count { it == QueensCellVisual.X_AUTO },
        )
        assertEquals(
            "MANUAL marks are cleared too (round-6 ruling, was: kept)",
            QueensCellVisual.EMPTY,
            after.visuals[15],
        )

        // ONE undo unit: the exact pre-clear board comes back — MANUAL mark included.
        state.undo()
        val restored = QueensBoardModel.of(state.session, state.hint, state.hintPointed)
        assertEquals(before.queens, restored.queens)
        assertEquals(before.visuals, restored.visuals)
        assertEquals(QueensCellVisual.X_MANUAL, restored.visuals[15])
    }

    @Test
    fun `a completed session cannot be cleared and locks every input`() {
        val solved = QueensUiFixtures.solutionCells.fold(QueensUiFixtures.session()) { acc, c ->
            acc.withQueenPlaced(c, QueensUiFixtures.T0)
        }
        assertTrue(solved.isComplete)
        val state = QueensBoardState(initialSession = solved)

        state.clearBoard()
        assertTrue("clearBoard no-ops on a completed session (button disabled anyway)", state.session.isComplete)
        assertEquals(QueensUiFixtures.solutionCells.map { it.idx }, state.session.queens.map { it.idx })
        state.onTap(0)
        assertEquals("taps are ignored after the win", QueensUiFixtures.solutionCells.map { it.idx }, state.session.queens.map { it.idx })
    }
}
