package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHint
import com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hint parity with ZipHintsTest: forced region/cell hints on live prefixes, incorrect
 * placements flagged, no false positives mid-game, and payloads that never leak the
 * decoded solution (A5) — region ids and cell coordinates only.
 */
class QueensHintsTest {

    private val hints = QueensHints()

    /** Empty board: the lowest-region forced queen — region 0's queen at (0,1) = cell 1. */
    @Test
    fun `empty board hints region 0's forced cell`() {
        assertEquals(QueensHint.Region(0, Cell(1)), hints.hint(QueensTestSupport.n4Puzzle(), emptyList()))
    }

    /** A live correct prefix: the next forced queen is region 1's cell 7. */
    @Test
    fun `correct prefix gets the next forced region`() {
        val puzzle = QueensTestSupport.n4Puzzle()
        assertEquals(QueensHint.Region(1, Cell(7)), hints.hint(puzzle, listOf(Cell(1))))
    }

    /** Forced deduction works from ANY consistent subset, not just prefixes. */
    @Test
    fun `scattered consistent subset still deduces the forced cell`() {
        val puzzle = QueensTestSupport.n4Puzzle()
        assertEquals(QueensHint.Region(0, Cell(1)), hints.hint(puzzle, listOf(Cell(8), Cell(14))))
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
        assertEquals(QueensHint.Region(1, Cell(13)), hints.hint(puzzle, listOf(Cell(1), Cell(3))))
    }

    /** No false positives mid-game: on the multi-solution board a crown belonging to one
     *  completion yields a valid next cell (region 0's queen of that completion), not a
     *  bogus Incorrect. */
    @Test
    fun `mid-game crown on an open board is never flagged incorrect`() {
        val puzzle = QueensTestSupport.n4OpenPuzzle()
        val hint = hints.hint(puzzle, listOf(Cell(2)))
        assertTrue("unexpected $hint", hint is QueensHint.Region)
        assertEquals(QueensHint.Region(0, Cell(4)), hint)
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
}
