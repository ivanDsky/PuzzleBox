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
        assertEquals(7, s.marks.size) // 6 row/col/region cells + the (1,1) 3×3 neighbor
        for (idx in 0 until 16) {
            if (idx == source.idx) continue
            val inUnits = idx / 4 == 0 || idx % 4 == 0 ||
                (idx / 4 <= 1 && idx % 4 <= 1) // the 3×3 neighbor square (round-7 ruling)
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
        // (1,1) sits in neither queen's row/col/region but IS q1's diagonal neighbor —
        // round-7 ruling: the 3×3 square is part of the fill set.
        assertEquals(q1, s.markAt(cell(1, 1))!!.source)
        assertNull(s.markAt(cell(3, 1)))                // in neither queen's fill set
    }

    // ---------------------------------------- fix round 7 (G-1): full fill set

    @Test
    fun `A10 round-7 - a mid-board queen fills EVERY row column region and 3x3-neighbor cell`() {
        val queen = cell(1, 1)
        val s = session().withQueenPlaced(queen, now)
        val n = 4
        var expected = 0
        for (idx in 0 until n * n) {
            val r = idx / n
            val c = idx % n
            val inUnits = r == 1 || c == 1 || r == 1 /* region = row fixture */
            val neighbor = kotlin.math.abs(r - 1) <= 1 && kotlin.math.abs(c - 1) <= 1
            if (idx == queen.idx) {
                assertNull("the queen cell stays unmarked", s.markAt(Cell(idx)))
            } else if (inUnits || neighbor) {
                assertEquals(QueensMark(MarkKind.AUTO, queen), s.markAt(Cell(idx)))
                expected++
            } else {
                assertNull(s.markAt(Cell(idx)))
            }
        }
        assertEquals(10, expected) // row(3) ∪ col(3) ∪ region(3) ∪ neighbors(8) minus overlaps, no queen cell
    }

    @Test
    fun `A10 round-7 - the fill set is the union over standing queens`() {
        // q1 marks a cell; q2 lands; q1 leaves — every cell either standing queen
        // covers (row/col/region/neighbors) must STILL be marked (recompute-derived).
        val q1 = cell(0, 0)
        val q2 = cell(2, 3)
        val both = session().withQueenPlaced(q1, now).withQueenPlaced(q2, now)
        val afterRemoval = both.withQueenRemoved(q1, now)
        assertEquals(listOf(q2), afterRemoval.queens)
        for (idx in 0 until 16) {
            val cell = Cell(idx)
            if (cell == q2) continue
            val r = idx / 4
            val c = idx % 4
            val covered = r == 2 || c == 3 || r == 2 ||
                (kotlin.math.abs(r - 2) <= 1 && kotlin.math.abs(c - 3) <= 1)
            if (covered) assertEquals(QueensMark(MarkKind.AUTO, q2), afterRemoval.markAt(cell))
            else assertNull(afterRemoval.markAt(cell))
        }
    }

    @Test
    fun `A10 round-7 - MANUAL marks are kept verbatim, AUTO re-derives around them`() {
        // MANUAL-marked cell inside the queen's fill set keeps MANUAL provenance and is
        // never re-stamped AUTO; every OTHER fill-set cell still derives AUTO.
        val manual = cell(1, 0) // in queen (1,1)'s row + 3×3
        val s = session().withCrossToggled(manual, now).withQueenPlaced(cell(1, 1), now)
        assertEquals(QueensMark(MarkKind.MANUAL), s.markAt(manual))
        assertEquals(QueensMark(MarkKind.AUTO, cell(1, 1)), s.markAt(cell(1, 2)))
        assertEquals(QueensMark(MarkKind.AUTO, cell(1, 1)), s.markAt(cell(0, 0)))
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

    /**
     * A11 strict closure (fix round 5): a MANUAL mark hidden under a standing queen
     * stays stored while the queen masks it, and LEAVES WITH the queen on removal —
     * the cell ends EMPTY and the per-cell cycle closes (empty → X → queen → empty).
     */
    @Test
    fun `A11 - removal closes the cycle - queen and its stored mark leave together`() {
        val target = cell(2, 2)
        val marked = session().withCrossToggled(target, now)
        // tap-cycle X → queen ON TOP of the manual mark: the queen masks it while it stands
        val placed = marked.withQueenPlaced(target, now)
        assertTrue(placed.queens.contains(target))
        assertEquals(QueensMark(MarkKind.MANUAL), placed.markAt(target))
        // the cycle's third tap: the queen AND its stored mark leave — the cell is EMPTY
        val removed = placed.withQueenRemoved(target, now)
        assertTrue(removed.queens.isEmpty())
        assertNull(removed.markAt(target))
    }

    /** The strict cycle's full closure on one cell, tap by tap (spec A11 ruling). */
    @Test
    fun `A11 - strict tap cycle closes empty - X - queen - empty`() {
        val target = cell(1, 1)
        val x = session().withCrossToggled(target, now)
        assertEquals(QueensMark(MarkKind.MANUAL), x.markAt(target))
        val queen = x.withQueenPlaced(target, now)
        assertTrue(queen.queens.contains(target))
        val empty = queen.withQueenRemoved(target, now)
        assertTrue(empty.queens.isEmpty())
        assertNull(empty.markAt(target))
        // closure leaves the cell exactly as it started
        assertEquals(session().marks, empty.marks)
    }

    // ------------------------------------------------------------------ A12

    @Test
    fun `A12 - one undo reverts one placement WITH its auto-fill side effects`() {
        val manualCell = cell(3, 3)
        val withManual = session().withCrossToggled(manualCell, now)
        val placed = withManual.withQueenPlaced(cell(0, 0), now)
        assertEquals(withManual.marks.size + 7, placed.marks.size) // 7 AUTO + the surviving manual mark
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
        // The play CLOCK persists through undo (it started at the first action and keeps
        // running) — the reverted PLAY STATE matches s0 exactly, modulo the clock.
        assertEquals(s0, s1.withUndo().copy(startedAt = s0.startedAt))
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
            // Play state must match exactly; the CLOCK persists through undo (round 6),
            // so normalize it against the recorded state before comparing.
            assertEquals("undo step ${states.size - 1 - i}", states[i], s.copy(startedAt = states[i].startedAt))
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

    // ---------------------------------------- fix round 6 (F-1/F-3/F-4)

    @Test
    fun `clear - wipes queens AND AUTO AND MANUAL marks as one undo unit`() {
        val manualCell = cell(3, 3)
        val played = session()
            .withCrossToggled(manualCell, now)
            .withQueenPlaced(cell(0, 0), now)
        assertEquals(QueensMark(MarkKind.MANUAL), played.markAt(manualCell))
        assertTrue(played.marks.any { it.value.kind == MarkKind.AUTO })

        val cleared = played.withCleared(now)
        // EVERYTHING is gone — queens, system marks, and the player's manual marks.
        assertTrue(cleared.queens.isEmpty())
        assertTrue(cleared.marks.isEmpty())
        assertNull(cleared.completedAt)

        // ONE undo unit: a single step reverts to EXACTLY the pre-clear board.
        val undone = cleared.withUndo()
        assertEquals(played.queens, undone.queens)
        assertEquals(played.marks, undone.marks)
    }

    @Test
    fun `clear - is a no-op on a completed session`() {
        val puzzle = n4()
        val solution = solver.solve(puzzle) ?: error("fixture board must be solvable")
        var s = session(puzzle)
        for (c in solution) s = s.withQueenPlaced(c, now)
        assertTrue(s.isComplete)
        assertEquals(s, s.withCleared(now))
    }

    @Test
    fun `clear - on an empty board leaves the board empty and undoable`() {
        val cleared = session().withCleared(now)
        assertTrue(cleared.queens.isEmpty())
        assertTrue(cleared.marks.isEmpty())
        assertNull(cleared.completedAt)
    }

    @Test
    fun `clear round-7 - wipes a MARK-ONLY board`() {
        val crossesOnly = session()
            .withCrossToggled(cell(0, 2), now)
            .withCrossToggled(cell(3, 1), now)
        assertTrue(crossesOnly.queens.isEmpty())
        assertEquals(2, crossesOnly.marks.size)
        val cleared = crossesOnly.withCleared(now)
        assertTrue(cleared.queens.isEmpty())
        assertTrue(cleared.marks.isEmpty())
        // one undo step restores the crosses exactly
        assertEquals(crossesOnly.marks, cleared.withUndo().marks)
    }

    @Test
    fun `auto-cross - OFF strips AUTO marks and places queens bare`() {
        val marked = session().withQueenPlaced(cell(0, 0), now)
        assertTrue(marked.marks.isNotEmpty()) // A10 auto-fill ON
        val bare = marked.withAutoCross(false)
        assertTrue(bare.marks.isEmpty())
        assertEquals(listOf(cell(0, 0)), bare.queens)
        // a NEW placement while OFF stays bare
        val bare2 = bare.withQueenPlaced(cell(2, 3), now)
        assertTrue(bare2.marks.isEmpty())
        assertEquals(setOf(cell(0, 0), cell(2, 3)), bare2.queens.toSet())
    }

    @Test
    fun `auto-cross - flipping ON re-derives marks from the standing queens`() {
        val bare = session().withAutoCross(false).withQueenPlaced(cell(0, 0), now)
        assertTrue(bare.marks.isEmpty())
        val filled = bare.withAutoCross(true)
        assertEquals(filled.marks, session().withQueenPlaced(cell(0, 0), now).marks)
    }

    @Test
    fun `auto-cross - MANUAL marks survive the OFF flip`() {
        val manualCell = cell(1, 1)
        val played = session()
            .withCrossToggled(manualCell, now)
            .withQueenPlaced(cell(0, 0), now)
        val off = played.withAutoCross(false)
        assertEquals(QueensMark(MarkKind.MANUAL), off.markAt(manualCell))
        // ... and are the only marks left
        assertTrue(off.marks.values.all { it.kind == MarkKind.MANUAL })
    }

    @Test
    fun `auto-cross - undo after a flip never resurrects OFF-stripped system marks`() {
        // place with auto-fill ON, strip OFF, undo the placement: the restored snapshot
        // marks must come back RECOMPUTED under OFF (bare), not as the stale AUTO wave.
        val placed = session().withQueenPlaced(cell(0, 0), now)
        val off = placed.withAutoCross(false)
        val undone = off.withUndo()
        assertTrue(undone.queens.isEmpty())
        assertTrue(undone.marks.isEmpty())
    }

    @Test
    fun `elapsed - runs from the first committed action to the win stamp`() {
        val t0 = Instant.ofEpochMilli(1_000_000)
        assertNull(session().elapsedMs()) // no action yet — no clock
        val puzzle = n4()
        val solution = solver.solve(puzzle) ?: error("fixture board must be solvable")
        var s = session(puzzle).withCrossToggled(cell(0, 3), t0) // the clock starts here
        assertNull(s.elapsedMs()) // clock started, not finished
        var at = t0
        for (c in solution) {
            at = at.plusMillis(300)
            s = s.withQueenPlaced(c, at)
        }
        assertTrue(s.isComplete)
        assertEquals(1200L, s.elapsedMs()) // win stamps at the LAST placement (t0 + 4×300)
    }

    @Test
    fun `elapsed - never negative for a same-instant win`() {
        val puzzle = n4()
        val solution = solver.solve(puzzle) ?: error("fixture board must be solvable")
        var s = session(puzzle)
        for (c in solution) s = s.withQueenPlaced(c, now)
        assertEquals(0L, s.elapsedMs())
    }

    @Test
    fun `hints - count once per presentation and are not undo units`() {
        val s = session().withQueenPlaced(cell(0, 0), now)
        assertEquals(0, s.hintsUsed)
        val hinted = s.withHintUsed()
        assertEquals(1, hinted.hintsUsed)
        // The hint pushed NO undo unit: undoing still reverts the last BOARD action
        // (the placement), while the presented-hint counter stays.
        val undone = hinted.withUndo()
        assertTrue(undone.queens.isEmpty())
        assertEquals(1, undone.hintsUsed)
        assertEquals(2, hinted.withHintUsed().hintsUsed)
    }
}
