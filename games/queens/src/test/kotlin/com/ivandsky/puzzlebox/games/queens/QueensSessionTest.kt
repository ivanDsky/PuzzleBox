package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSolver
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A10–A12 state machine (A11 ruling reversed 2026-11-02: queen MOVE deleted — the
 * interaction is the strict tap cycle): auto-fill lifecycle (AUTO marks live only while
 * their source queen stands), and one undo step reverts one action WITH its auto-fill
 * side effects while earlier manual marks survive.
 */
class QueensSessionTest {

    private val now = Instant.ofEpochMilli(1_700_000_000_000)
    private val solver = QueensSolver()

    /** 4×4, region = row: solvable by (row0:c1, row1:c3, row2:c0, row3:c2)-style layouts. */
    private fun n4() = QueensTestSupport.puzzle(4) { r, _ -> r }

    private fun cell(row: Int, col: Int, size: Int = 4) = QueensTestSupport.cell(row, col, size)

    private fun session(puzzle: QueensPuzzle = n4()) = QueensSession(ref = null, puzzle = puzzle)

    // ------------------------------------------------------------------ A10

    @Test
    fun `A10 - placing a queen auto-fills its row column and region as AUTO marks sourced by it`() {
        val s = session().withQueenPlaced(cell(0, 0), now)
        assertEquals(listOf(cell(0, 0)), s.queens)
        val source = cell(0, 0)
        // row 0 ∪ column 0 ∪ region (row) 0 = 7 cells; the queen cell carries no mark
        assertEquals(6, s.marks.size)
        for (idx in 0 until 16) {
            if (idx == source.idx) continue
            val inUnits = idx / 4 == 0 || idx % 4 == 0
            if (inUnits) {
                val mark = s.markAt(Cell(idx))!!
                assertEquals(MarkKind.AUTO, mark.kind)
                assertEquals(source, mark.source)
            } else {
                assertNull(s.markAt(Cell(idx)))
            }
        }
    }

    @Test
    fun `A10 - auto-fill never overwrites an existing MANUAL mark`() {
        val manualCell = cell(2, 2)
        val s = session().withCrossToggled(manualCell, now).withQueenPlaced(cell(0, 0), now)
        assertEquals(QueensMark(MarkKind.MANUAL), s.markAt(manualCell))
        // and the manual cell is NOT re-stamped AUTO
        assertEquals(1, s.marks.values.count { it.kind == MarkKind.MANUAL })
    }

    @Test
    fun `A10 - auto-fill never overwrites another queen's AUTO mark`() {
        // place two queens that do not share row/col so both auto-fills coexist
        val q1 = cell(0, 0)
        val q2 = cell(2, 3)
        val s = session().withQueenPlaced(q1, now).withQueenPlaced(q2, now)
        assertEquals(setOf(q1, q2), s.queens.toSet())
        assertEquals(q1, s.markAt(cell(0, 3))!!.source) // q1's row
        assertEquals(q2, s.markAt(cell(2, 1))!!.source) // q2's row
        assertNull(s.markAt(cell(1, 1)))                // in neither queen's units
    }

    @Test
    fun `A10 lifecycle - removing a queen clears exactly its AUTO marks, MANUAL marks survive`() {
        val manualCell = cell(3, 3)
        val q1 = cell(0, 0)
        val q2 = cell(2, 3)
        val s = session()
            .withCrossToggled(manualCell, now)
            .withQueenPlaced(q1, now)
            .withQueenPlaced(q2, now)
            .withQueenRemoved(q1, now)
        assertFalse(s.queens.contains(q1))
        // every remaining AUTO mark is sourced by q2
        assertTrue(s.marks.values.none { it.kind == MarkKind.AUTO && it.source == q1 })
        assertEquals(q2, s.markAt(cell(2, 1))!!.source)
        assertEquals(QueensMark(MarkKind.MANUAL), s.markAt(manualCell))
    }

    // ------------------------------------------------------------------ A11 (strict tap cycle — move deleted)

    @Test
    fun `A11 - tapping a queen removes it and its AUTO marks, MANUAL marks survive`() {
        val manualCell = cell(2, 1)
        val s = session()
            .withQueenPlaced(cell(0, 0), now)
            .withCrossToggled(manualCell, now)
            .withQueenRemoved(cell(0, 0), now)
        assertTrue(s.queens.isEmpty())
        // The removed queen's AUTO marks are gone entirely...
        assertTrue(s.marks.values.none { it.kind == MarkKind.AUTO })
        // ...while the MANUAL mark is untouched.
        assertEquals(QueensMark(MarkKind.MANUAL), s.markAt(manualCell))
    }

    @Test
    fun `A11 - no-ops change nothing`() {
        val s = session().withQueenPlaced(cell(0, 0), now)
        assertEquals(s, s.withQueenRemoved(cell(1, 1), now)) // no queen there
        assertEquals(s, s.withQueenPlaced(cell(0, 0), now)) // cell occupied
    }

    /** MANUAL marks hidden under a standing queen reappear when it is removed. */
    @Test
    fun `A11 - MANUAL mark under a standing queen reappears on removal`() {
        val target = cell(2, 2)
        val marked = session().withCrossToggled(target, now)
        // tap-cycle X → queen ON TOP of the manual mark: the queen masks it while it stands
        val placed = marked.withQueenPlaced(target, now)
        assertTrue(placed.queens.contains(target))
        assertEquals(QueensMark(MarkKind.MANUAL), placed.markAt(target))
        val removed = placed.withQueenRemoved(target, now)
        assertEquals(QueensMark(MarkKind.MANUAL), removed.markAt(target))
    }

    // ------------------------------------------------------------------ A12

    @Test
    fun `A12 - one undo reverts one placement WITH its auto-fill side effects`() {
        val manualCell = cell(3, 3)
        val withManual = session().withCrossToggled(manualCell, now)
        val placed = withManual.withQueenPlaced(cell(0, 0), now)
        assertEquals(withManual.marks.size + 6, placed.marks.size) // 6 AUTO + the surviving manual mark
        val undone = placed.withUndo()
        assertEquals(withManual, undone) // manual cross survived; all AUTO marks gone
        assertEquals(QueensMark(MarkKind.MANUAL), undone.markAt(manualCell))
        assertEquals(emptyList<Cell>(), undone.queens)
    }

    @Test
    fun `A12 - undo walks back action by action`() {
        val s0 = session()
        val s1 = s0.withQueenPlaced(cell(0, 0), now)
        val s2 = s1.withQueenRemoved(cell(0, 0), now)
        val s3 = s2.withCrossToggled(cell(1, 1), now)
        assertEquals(s2, s3.withUndo())
        assertEquals(s1, s2.withUndo())
        assertEquals(s0, s1.withUndo())
        assertEquals(s0, s0.withUndo()) // empty stack is a no-op
    }

    @Test
    fun `A12 - property - undoing k steps equals the state after k actions`() {
        var s = session()
        val states = ArrayList<QueensSession>()
        states.add(s)
        // a deterministic action script mixing placements, removals, manual marks
        val script: List<(QueensSession) -> QueensSession> = listOf(
            { it.withQueenPlaced(cell(0, 0), now) },
            { it.withQueenPlaced(cell(2, 3), now) },
            { it.withCrossToggled(cell(1, 1), now) },
            { it.withQueenRemoved(cell(0, 0), now) },
            { it.withQueenPlaced(cell(3, 3), now) },
        )
        for (action in script) s = action(s).also { states.add(it) }
        for (i in states.indices.reversed()) {
            assertEquals("undo step ${states.size - 1 - i}", states[i], s)
            s = s.withUndo()
        }
    }

    // ------------------------------------------------------- win detection

    @Test
    fun `win - completing the board stamps completedAt exactly when the verifier is clean`() {
        val puzzle = n4()
        val solution = solver.solve(puzzle) ?: error("fixture board must be solvable")
        var s = session(puzzle)
        for (cell in solution) s = s.withQueenPlaced(cell, now)
        assertEquals(solution.size, s.queens.size)
        assertTrue(com.ivandsky.puzzlebox.games.queens.engine.rules.QueensVerifier.isSolution(puzzle, s.queens.toSet()))
        assertEquals(now, s.completedAt)
        // a completed session is frozen
        assertEquals(s, s.withQueenPlaced(solution.first(), now))
        assertEquals(s, s.withUndo())
        // but the win state round-trips through undo to pre-win
        assertEquals(true, s.isComplete)
    }

    @Test
    fun `no win - a full board that is not a solution does not complete`() {
        val puzzle = n4()
        var s = session(puzzle)
        for (col in 0 until 4) s = s.withQueenPlaced(cell(col, col), now) // diagonal: touching
        assertEquals(4, s.queens.size)
        assertNull(s.completedAt)
        assertFalse(s.isComplete)
    }

    @Test
    fun `cross toggle - sets then clears a MANUAL mark on queen-free cells only`() {
        val s1 = session().withCrossToggled(cell(1, 1), now)
        assertEquals(QueensMark(MarkKind.MANUAL), s1.markAt(cell(1, 1)))
        val s2 = s1.withCrossToggled(cell(1, 1), now)
        assertNull(s2.markAt(cell(1, 1)))
        // queen cells are never cross-marked
        val s3 = session().withQueenPlaced(cell(0, 0), now)
        assertEquals(s3, s3.withCrossToggled(cell(0, 0), now))
        // toggling an AUTO cell overrides it with a MANUAL mark (user intent wins)
        val s4 = s3.withCrossToggled(cell(0, 1), now)
        assertEquals(QueensMark(MarkKind.MANUAL), s4.markAt(cell(0, 1)))
        // ... and undo restores the AUTO provenance
        assertEquals(s3, s4.withUndo())
    }
}
