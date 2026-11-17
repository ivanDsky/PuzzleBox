package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.gameui.MoveHaptic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2 for Queens: a queen or mark placed or removed by a tap ticks, the solving queen included; undo, redo, clear,
 * hints and auto-cross are silent. Queens refuses nothing, so it never buzzes.
 */
class QueensMoveHapticTest {

    private val now = QueensUiFixtures.T0
    private val solution = QueensUiFixtures.solutionCells
    private val start = QueensUiFixtures.session()

    private fun haptic(before: QueensSession, after: QueensSession): MoveHaptic? = queensMoveHaptic(before, after)

    @Test
    fun `a queen placed by a tap ticks`() {
        assertEquals(MoveHaptic.Placed, haptic(start, start.withQueenPlaced(solution[0], now)))
    }

    @Test
    fun `a queen removed by a tap ticks`() {
        val placed = start.withQueenPlaced(solution[0], now)
        assertEquals(MoveHaptic.Placed, haptic(placed, placed.withQueenRemoved(solution[0], now)))
    }

    @Test
    fun `a cross placed or removed by a tap ticks`() {
        val crossed = start.withCrossToggled(Cell(0), now)
        assertEquals(MoveHaptic.Placed, haptic(start, crossed))
        assertEquals(MoveHaptic.Placed, haptic(crossed, crossed.withCrossToggled(Cell(0), now)))
    }

    @Test
    fun `an erase swipe that lifts a cross ticks`() {
        val crossed = start.withCrossToggled(Cell(0), now)
        assertEquals(MoveHaptic.Placed, haptic(crossed, crossed.withCrossErased(Cell(0), extendRun = false, now = now)))
    }

    @Test
    fun `every step of an erase swipe ticks, not only the first`() {
        val crossed = start.withCrossToggled(Cell(0), now).withCrossToggled(Cell(1), now)
        val first = crossed.withCrossErased(Cell(0), extendRun = false, now = now)
        assertEquals(MoveHaptic.Placed, haptic(crossed, first))
        assertEquals(MoveHaptic.Placed, haptic(first, first.withCrossErased(Cell(1), extendRun = true, now = now)))
    }

    @Test
    fun `an erase step that lifts nothing is silent`() {
        val crossed = start.withCrossToggled(Cell(0), now)
        val erased = crossed.withCrossErased(Cell(0), extendRun = false, now = now)
        assertNull(haptic(erased, erased.withCrossErased(Cell(0), extendRun = true, now = now)))
    }

    @Test
    fun `a queen that conflicts with a standing one still ticks, never buzzes`() {
        val one = start.withQueenPlaced(solution[0], now)
        val sameRow = one.withQueenPlaced(Cell(solution[0].idx + 1), now)
        assertEquals(MoveHaptic.Placed, haptic(one, sameRow))
    }

    @Test
    fun `two placements between two reads tick once`() {
        val both = start.withQueenPlaced(solution[0], now).withQueenPlaced(solution[1], now)
        assertEquals(MoveHaptic.Placed, haptic(start, both))
    }

    @Test
    fun `re-placing a queen that was just undone ticks, by identity not equality`() {
        val crossed = start.withCrossToggled(Cell(0), now)
        val undone = crossed.withQueenPlaced(solution[0], now).withUndo()
        assertEquals(MoveHaptic.Placed, haptic(undone, undone.withQueenPlaced(solution[0], now)))
    }

    @Test
    fun `two redos between two reads are silent`() {
        val placed = start.withQueenPlaced(solution[0], now).withQueenPlaced(solution[1], now)
        val undone = placed.withUndo().withUndo()
        assertTrue(undone.history.done.isEmpty())
        assertNull(haptic(undone, undone.withRedo().withRedo()))
    }

    @Test
    fun `the queen that solves the board ticks like any other, ahead of the solve confirm`() {
        val three = solution.dropLast(1).fold(start) { s, cell -> s.withQueenPlaced(cell, now) }
        val solved = three.withQueenPlaced(solution.last(), now)
        assertTrue(solved.isComplete)
        assertEquals(MoveHaptic.Placed, haptic(three, solved))
    }

    @Test
    fun `undo and redo are silent`() {
        val placed = start.withQueenPlaced(solution[0], now)
        val undone = placed.withUndo()
        assertNull(haptic(placed, undone))
        assertNull(haptic(undone, undone.withRedo()))
    }

    @Test
    fun `redo that empties the redo stack is silent`() {
        val placed = start.withQueenPlaced(solution[0], now).withQueenPlaced(solution[1], now)
        val undone = placed.withUndo()
        assertTrue(undone.history.undone.isNotEmpty())
        assertNull(haptic(undone, undone.withRedo()))
    }

    @Test
    fun `clear is silent`() {
        val placed = start.withQueenPlaced(solution[0], now)
        assertNull(haptic(placed, placed.withCleared(now)))
    }

    @Test
    fun `a hint is silent`() {
        assertNull(haptic(start, start.withHintUsed()))
    }

    @Test
    fun `auto-cross on or off is silent`() {
        val placed = start.withQueenPlaced(solution[0], now)
        assertNull(haptic(placed, placed.withAutoCross(!placed.autoCross)))
    }

    @Test
    fun `input that changes nothing is silent`() {
        assertNull(haptic(start, start))
    }
}
