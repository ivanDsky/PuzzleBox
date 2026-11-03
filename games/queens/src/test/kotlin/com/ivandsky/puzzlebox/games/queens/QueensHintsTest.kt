package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason
import com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHint
import com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hint parity with ZipHintsTest: forced region/cell hints on live prefixes, incorrect
 * placements flagged, no false positives mid-game, and payloads that never leak the
 * decoded solution (A5) — region ids and cell coordinates only. Fix round 6 adds the
 * staged ladder's stage-1 deduction ([HintReason]): the ROW/COLUMN/REGION unit whose
 * every other cell is directly blocked by the PLACED queens, ROW → COLUMN → REGION
 * priority, null when only exhaustive search can prove the forcing.
 */
class QueensHintsTest {

    private val hints = QueensHints()

    /** Empty board: the lowest-region forced queen — region 0's queen at (0,1) = cell 1.
     *  No placed premises → no human-checkable elimination → the neutral fallback (null). */
    @Test
    fun `empty board hints region 0's forced cell`() {
        assertEquals(QueensHint.Region(0, Cell(1), null), hints.hint(QueensTestSupport.n4Puzzle(), emptyList()))
    }

    /** A live correct prefix: the next forced queen is region 1's cell 7, proven by its
     *  ROW — every other row-1 cell is directly blocked by the placed (0,1) queen. */
    @Test
    fun `correct prefix gets the next forced region`() {
        val puzzle = QueensTestSupport.n4Puzzle()
        assertEquals(
            QueensHint.Region(1, Cell(7), HintReason(HintReason.Kind.ROW, 1)),
            hints.hint(puzzle, listOf(Cell(1))),
        )
    }

    /** Forced deduction works from ANY consistent subset, not just prefixes. With
     *  (2,0)+(3,2) placed, row 0 keeps an unblocked cell — the proof is COLUMN 1. */
    @Test
    fun `scattered consistent subset still deduces the forced cell`() {
        val puzzle = QueensTestSupport.n4Puzzle()
        assertEquals(
            QueensHint.Region(0, Cell(1), HintReason(HintReason.Kind.COLUMN, 1)),
            hints.hint(puzzle, listOf(Cell(8), Cell(14))),
        )
    }

    /** Fix round 6 priority: ROW wins when ROW and COLUMN BOTH prove the forcing. */
    @Test
    fun `reason priority - ROW beats COLUMN and REGION`() {
        val puzzle = QueensTestSupport.n4Puzzle()
        val hint = hints.hint(puzzle, listOf(Cell(7), Cell(8), Cell(14)))
        val reason = (hint as QueensHint.Region).reason
        assertEquals(HintReason(HintReason.Kind.ROW, 0), reason)
    }

    /** A placed crown off the stored solution is flagged — never the solution itself. */
    @Test
    fun `misplaced crown gets an incorrect hint`() {
        val puzzle = QueensTestSupport.n4Puzzle()
        assertEquals(QueensHint.Incorrect(listOf(Cell(0))), hints.hint(puzzle, listOf(Cell(0))))
    }

    /** Two crowns in one row break the row rule before any solution check. */
    @Test
    fun `rule-violating crowns are attributed as incorrect`() {
        val puzzle = QueensTestSupport.n4Puzzle()
        assertEquals(
            QueensHint.Incorrect(listOf(Cell(1), Cell(3))),
            hints.hint(puzzle, listOf(Cell(1), Cell(3))),
        )
    }

    /** The whole solution placed: nothing to hint. */
    @Test
    fun `completed board hints none`() {
        val puzzle = QueensTestSupport.n4Puzzle()
        assertEquals(QueensHint.None, hints.hint(puzzle, puzzle.solution!!.toList()))
    }

    /** N=2 (Star Battle shape): the forced queen is the lowest-region one of the rest. */
    @Test
    fun `star battle board deduces the next forced queen`() {
        val puzzle = QueensTestSupport.n8N2Puzzle()
        val hint = hints.hint(puzzle, listOf(Cell(1), Cell(3)))
        // Reason shape varies with this fixture's region layout — payload only.
        assertTrue("unexpected $hint", hint is QueensHint.Region)
        hint as QueensHint.Region
        assertEquals(1, hint.region)
        assertEquals(Cell(13), hint.cell)
    }

    /** No false positives mid-game: on the multi-solution board a crown belonging to one
     *  completion yields a valid next cell (region 0's queen of that completion), not a
     *  bogus Incorrect. */
    @Test
    fun `mid-game crown on an open board is never flagged incorrect`() {
        val puzzle = QueensTestSupport.n4OpenPuzzle()
        val hint = hints.hint(puzzle, listOf(Cell(2)))
        // Reason shape varies with the open board's regions — assert the PAYLOAD only.
        assertTrue("unexpected $hint", hint is QueensHint.Region)
        hint as QueensHint.Region
        assertEquals(0, hint.region)
        assertEquals(Cell(4), hint.cell)
    }

    /** Zip parity: blind boards (no stored solution) answer exactly like informed ones
     *  when the placed crowns are consistent. */
    @Test
    fun `hints work without a stored solution`() {
        val informed = QueensTestSupport.n4Puzzle()
        val blind = informed.copy(solution = null)
        listOf(emptyList<Cell>(), listOf(Cell(1)), listOf(Cell(8), Cell(14))).forEach { placed ->
            assertEquals("placed=$placed", hints.hint(informed, placed), hints.hint(blind, placed))
        }
    }

    /** Blind board with a dead placement: the crowns cannot all stay — still no leak,
     *  only cell coordinates. */
    @Test
    fun `blind dead placement reports the unkeepable crowns`() {
        val blind = QueensTestSupport.n4Puzzle().copy(solution = null)
        assertEquals(QueensHint.Incorrect(listOf(Cell(0))), hints.hint(blind, listOf(Cell(0))))
    }

    /** A5: hint payloads carry region ids / cell coords only — never the solution. */
    @Test
    fun `payloads never expose the solution`() {
        val puzzle = QueensTestSupport.n4Puzzle()
        listOf(emptyList<Cell>(), listOf(Cell(1)), listOf(Cell(0)), listOf(Cell(1), Cell(3)))
            .forEach { placed ->
                val hint = hints.hint(puzzle, placed)
                when (hint) {
                    is QueensHint.Region -> assertTrue(hint.cell.idx in 0 until puzzle.cellCount)
                    is QueensHint.Incorrect ->
                        hint.cells.forEach { assertTrue(it.idx in 0 until puzzle.cellCount) }
                    QueensHint.None -> {}
                }
                assertTrue(hint.toString().let { s -> !s.contains(puzzle.solution.toString()) })
            }
    }

    /**
     * Fix round 6 — the REGION-only elimination: with rows failing and columns failing,
     * the region itself (every other region-0 cell directly blocked) is the proof. Uses
     * the internal helper directly (it must stay internal — the UI renders captions from
     * the payload, it never re-derives).
     */
    @Test
    fun `direct elimination - REGION unit when row and column stay unprovable`() {
        // Regions hand-picked so placed (2,3)+(3,2) block every OTHER region-0 cell
        // ((1,3) via column 3, (3,1) via row 3) while row 0 and column 0 keep free cells.
        val regions = listOf(
            0, 2, 2, 3,
            2, 2, 2, 0,
            3, 2, 3, 1,
            3, 0, 1, 3,
        )
        val puzzle = QueensTestSupport.puzzle(4) { row, col -> regions[row * 4 + col] }
        val reason = hints.directEliminationReason(puzzle, setOf(Cell(11), Cell(14)), Cell(0))
        assertEquals(HintReason(HintReason.Kind.REGION, 0), reason)
    }

    /** The neutral fallback contract: no placed premises → no reason → stage 1 stays neutral. */
    @Test
    fun `direct elimination - null with no placed queens`() {
        val reason = hints.directEliminationReason(QueensTestSupport.n4Puzzle(), emptySet(), Cell(1))
        assertNull(reason)
    }
}
