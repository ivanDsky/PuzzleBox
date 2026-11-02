package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.game.Move
import com.ivandsky.puzzlebox.games.zip.engine.ZipMove
import com.ivandsky.puzzlebox.games.zip.engine.ZipRules
import com.ivandsky.puzzlebox.games.zip.engine.ZipState
import com.ivandsky.puzzlebox.games.zip.engine.ZipValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O(1) move validation against the hand-built forced board (I1–I8) plus the retract and
 * reset flows. Each test pins a specific predicate: deleting that predicate in ZipRules
 * flips the asserted verdict and fails the suite (mutation falsification, F2 §9).
 */
class ZipRulesPropertyTest {

    private val rules = ZipRules
    private val puzzle = ZipTestSupport.n5Puzzle()

    private fun state(path: List<Cell>) = ZipState(puzzle, path)

    private fun extend(to: Cell, path: List<Cell>) =
        rules.validateMove(ZipMove.Extend(to), state(path))

    // I8 bounds: off-board cells are OutOfBounds before any other check.
    @Test
    fun `I8 - extend off the board is OutOfBounds`() {
        assertEquals(
            ZipValidation.Invalid(ZipValidation.Reason.OutOfBounds, Cell(25)),
            extend(Cell(25), listOf(Cell(0))),
        )
        assertEquals(
            ZipValidation.Invalid(ZipValidation.Reason.OutOfBounds, Cell(25)),
            extend(Cell(25), emptyList()), // bounds precede the start check
        )
        assertEquals(
            ZipValidation.Invalid(ZipValidation.Reason.OutOfBounds, Cell(-1)),
            extend(Cell(-1), emptyList()),
        )
    }

    // I4 start: the empty trace only accepts waypoint 1.
    @Test
    fun `I4 - empty trace must start at waypoint 1`() {
        assertEquals(ZipValidation.Valid, extend(Cell(0), emptyList()))
        assertEquals(
            ZipValidation.Invalid(ZipValidation.Reason.WrongStart, Cell(5)),
            extend(Cell(5), emptyList()),
        )
        assertEquals(
            ZipValidation.Invalid(ZipValidation.Reason.WrongStart, Cell(24)),
            extend(Cell(24), emptyList()), // even the end waypoint is not the start
        )
    }

    // I1 adjacency: diagonal or distant steps are NotAdjacent.
    @Test
    fun `I1 - non-adjacent steps are rejected`() {
        val path = ZipTestSupport.path(0, 1)
        assertEquals(
            ZipValidation.Invalid(ZipValidation.Reason.NotAdjacent, Cell(7)),
            extend(Cell(7), path), // diagonal
        )
        assertEquals(
            ZipValidation.Invalid(ZipValidation.Reason.NotAdjacent, Cell(24)),
            extend(Cell(24), path), // far away
        )
    }

    // I2 walls: the forced board walls 0->5; stepping into the wall is WallBetween.
    // Deleting the wall check in validateMove flips this to Valid -> suite fails.
    @Test
    fun `I2 - moving across a wall is WallBetween`() {
        assertEquals(
            ZipValidation.Invalid(ZipValidation.Reason.WallBetween, Cell(5)),
            extend(Cell(5), listOf(Cell(0))),
        )
        assertEquals(
            ZipValidation.Invalid(ZipValidation.Reason.WallBetween, Cell(6)),
            extend(Cell(6), listOf(Cell(1))), // DOWN wall at 1
        )
    }

    // I3 revisit: no cell twice.
    @Test
    fun `I3 - revisiting a trace cell is CellAlreadyInPath`() {
        assertEquals(
            ZipValidation.Invalid(ZipValidation.Reason.CellAlreadyInPath, Cell(0)),
            extend(Cell(0), ZipTestSupport.path(0, 1)),
        )
        assertEquals(
            ZipValidation.Invalid(ZipValidation.Reason.CellAlreadyInPath, Cell(3)),
            extend(Cell(3), ZipTestSupport.path(0, 1, 2, 3, 4)),
        )
    }

    // I5 waypoint order: reaching waypoint 3 before waypoint 2 is WrongWaypointOrder.
    @Test
    fun `I5 - out-of-order waypoint names the expected cell`() {
        val open = ZipPuzzle(
            puzzle.variant,
            ZipBoard(5, emptySet(), listOf(Cell(0), Cell(7), Cell(24))),
            null,
        )
        // walk the left column and bottom row: ends adjacent to 24 (label 3) with 7 (label 2) unvisited
        val openState = ZipState(open, ZipTestSupport.path(0, 5, 10, 15, 20, 21, 22, 23))
        assertEquals(
            ZipValidation.Invalid(ZipValidation.Reason.WrongWaypointOrder(Cell(7)), Cell(24)),
            rules.validateMove(ZipMove.Extend(Cell(24)), openState),
        )
    }

    @Test
    fun `I5 - in-order waypoint steps validate`() {
        val path = ZipTestSupport.path(0, 1, 2, 3, 4, 9, 8, 7, 6, 5, 10, 11)
        assertEquals(ZipValidation.Valid, extend(Cell(12), path)) // label 2, expected 2
    }

    // I6/I7 completeness: full path ending at the last waypoint, verified linearly.
    @Test
    fun `I6-I7 - completeness requires full coverage and the end waypoint`() {
        assertTrue(rules.isComplete(state(ZipTestSupport.n5Solution)))

        val missingLast = ZipTestSupport.n5Solution.dropLast(1)
        assertFalse(rules.isComplete(state(missingLast)))

        val wrongEnd = ZipTestSupport.n5Solution.toMutableList().also { it[it.lastIndex] = Cell(23) }
        // swap keeps 24 unvisited and ends at 23: not the end waypoint
        assertFalse(rules.isComplete(state(wrongEnd)))

        val invalidOrder = listOf(Cell(0), Cell(1)) + ZipTestSupport.n5Solution.drop(2).asReversed()
        assertFalse(rules.isComplete(state(invalidOrder)))
    }

    // Retract flow: to a trace cell is Valid (trim applied by the session), off-trace is NotInPath.
    @Test
    fun `retract to a trace cell validates and off-trace targets are NotInPath`() {
        val path = ZipTestSupport.path(0, 1, 2, 3)
        assertEquals(ZipValidation.Valid, rules.validateMove(ZipMove.RetractTo(Cell(2)), state(path)))
        assertEquals(ZipValidation.Valid, rules.validateMove(ZipMove.RetractTo(Cell(3)), state(path))) // tail: no-op
        assertEquals(
            ZipValidation.Invalid(ZipValidation.Reason.NotInPath, Cell(4)),
            rules.validateMove(ZipMove.RetractTo(Cell(4)), state(path)),
        )
    }

    // The GameRules contract: a foreign Move is safe-cast into a typed rejection — never an `as` crash.
    @Test
    fun `foreign move is a typed rejection, never a ClassCastException`() {
        val verdict = rules.validate(object : Move {}, state(ZipTestSupport.path(0)))
        assertEquals(ZipValidation.Invalid(ZipValidation.Reason.ForeignMove), verdict)
        assertEquals(ZipValidation.Valid, rules.validate(ZipMove.Reset, state(emptyList())))
    }

    @Test
    fun `reset always validates`() {
        assertEquals(
            ZipValidation.Valid,
            rules.validateMove(ZipMove.Reset, state(ZipTestSupport.n5Solution)),
        )
    }

    // verifyPath: the linear oracle half (A16 input validation).
    @Test
    fun `verifyPath accepts the shipped solution and rejects violations`() {
        assertTrue(ZipRules.verifyPath(puzzle, ZipTestSupport.n5Solution))
        assertFalse(ZipRules.verifyPath(puzzle, emptyList()))
        assertFalse(ZipRules.verifyPath(puzzle, listOf(Cell(5)))) // wrong start
        assertFalse(ZipRules.verifyPath(puzzle, listOf(Cell(0), Cell(5)))) // crosses wall
        assertFalse(ZipRules.verifyPath(puzzle, ZipTestSupport.path(0, 1, 2, 2))) // revisit
        assertFalse(ZipRules.verifyPath(puzzle, ZipTestSupport.path(0, 1, 26))) // off board
        assertFalse(ZipRules.verifyPath(puzzle, ZipTestSupport.path(0, 7))) // not adjacent
    }
}
