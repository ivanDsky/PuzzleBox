package com.ivandsky.puzzlebox.games.zip.engine

import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import com.ivandsky.puzzlebox.games.zip.ZipTestSupport
import com.ivandsky.puzzlebox.games.zip.path
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The first-move rule (nav-rethink B4) over fake proofs: a hint never points at a step the player
 * already knows, and lands on the first real choice that is proven forced.
 */
class ZipFirstMovePickerTest {

    /** Open 3x3, 1 at the top-left and 2 at the bottom-right; [openLine] is one way through it. */
    private val open = ZipPuzzle(ZipTestSupport.variantOf(5).id, ZipBoard(3, emptySet(), path(0, 8)), solution = null)
    private val openLine = path(0, 1, 2, 5, 4, 3, 6, 7, 8)

    /** 5x5 corridor with one side door at cell 3: the only real choice on the way is the step after 3. */
    private val branchy = ZipTestSupport.n5BranchyPuzzle()
    private val corridor = ZipTestSupport.n5Puzzle()
    private val line = ZipTestSupport.n5Solution

    private val alwaysForced = ZipFirstMovePicker { _, _, _ -> true }
    private val neverForced = ZipFirstMovePicker { _, _, _ -> false }

    @Test
    fun `an empty board never gets its start cell, the hint lands on the first real choice`() {
        val pick = alwaysForced.pick(open, emptyList(), openLine)

        assertEquals(ZipHint.Step(Cell(1), lead = path(0)), pick)
    }

    @Test
    fun `steps with one way forward are walked over, not hinted`() {
        val pick = alwaysForced.pick(branchy, emptyList(), line)

        // 0, 1, 2 and 3 each have one way on; after 3 the trace may go to 4 or through the door to 8.
        assertEquals(ZipHint.Step(Cell(4), lead = path(0, 1, 2, 3)), pick)
    }

    @Test
    fun `a real choice that is not proven forced is passed for the first one that is`() {
        val asked = mutableListOf<Pair<List<Cell>, List<Cell>>>()
        val forcedOnlyAfterOne = ZipFirstMovePicker { _, prefix, others ->
            asked += prefix to others
            prefix == path(0, 1)
        }

        val pick = forcedOnlyAfterOne.pick(open, emptyList(), openLine)

        assertEquals(ZipHint.Step(Cell(2), lead = path(0, 1)), pick)
        // The proof is asked about real choices only, with the other legal cells: never about the start cell.
        assertEquals(listOf(path(0) to path(3), path(0, 1) to path(4)), asked)
    }

    @Test
    fun `with no choice proven forced the hint is the first real choice`() {
        val pick = neverForced.pick(open, emptyList(), openLine)

        assertEquals(ZipHint.Step(Cell(1), lead = path(0)), pick)
    }

    @Test
    fun `mid trace the hint starts from the head, not from the start`() {
        val pick = alwaysForced.pick(branchy, path(0, 1), line)

        assertEquals(ZipHint.Step(Cell(4), lead = path(2, 3)), pick)
    }

    @Test
    fun `with no real choice left the hint is one step, and still never the start cell`() {
        assertEquals(ZipHint.Step(Cell(3)), alwaysForced.pick(corridor, path(0, 1, 2), line))
        assertEquals(ZipHint.Step(Cell(1), lead = path(0)), alwaysForced.pick(corridor, emptyList(), line))
    }

    @Test
    fun `the engine entry proves the choice with the solver when the board stores no solution`() {
        val hints = ZipHints()
        val unsolved = branchy.copy(solution = null)

        assertEquals(ZipHint.Step(Cell(4), lead = path(0, 1, 2, 3)), hints.firstMove(ZipState(unsolved, emptyList())))
        assertEquals(ZipHint.Step(Cell(4), lead = path(0, 1, 2, 3)), hints.firstMove(ZipState(branchy, emptyList())))
        // Legacy is today's step: the start cell.
        assertEquals(ZipHint.Step(Cell(0)), hints.hint(ZipState(branchy, emptyList())))
    }

    @Test
    fun `a dead trace is still trimmed first`() {
        val dead = ZipState(branchy, path(0, 1, 2, 3, 8))

        assertEquals(ZipHint.Backtrack(3), ZipHints().firstMove(dead))
    }
}
