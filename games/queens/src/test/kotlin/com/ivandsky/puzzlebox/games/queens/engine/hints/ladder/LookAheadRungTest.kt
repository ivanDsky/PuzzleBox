package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The look-ahead rung (spec A9, ticket 08): a cell is excluded when a queen there, propagated
 * by the lower rungs, empties a row, column or region.
 */
class LookAheadRungTest {

    private val lookAhead = LookAheadRung(listOf(SinglesRung))

    /**
     * 6×6, unique solution 0, 8, 16, 19, 27, 35; no single on the empty board. A queen on
     * cell 1 lets singles run until region 5 (cells 34, 35) has no open cell left; without
     * propagation the first contradiction is cell 22 emptying region 2 at once.
     */
    private fun needsLookAhead(): QueensPuzzle = QueensPuzzle(
        size = 6,
        regions = listOf(
            0, 0, 0, 0, 1, 1,
            0, 0, 1, 1, 1, 1,
            0, 3, 1, 2, 2, 2,
            0, 3, 3, 3, 3, 2,
            0, 3, 3, 4, 4, 4,
            3, 3, 3, 4, 5, 5,
        ),
        solution = QueensTestSupport.path(0, 8, 16, 19, 27, 35),
    )

    private fun start(puzzle: QueensPuzzle) = QueensCandidates.start(puzzle, emptySet())

    @Test
    fun `a probe that empties a region excludes the probed cell`() {
        val puzzle = needsLookAhead()
        assertNull(SinglesRung.step(puzzle, start(puzzle)))

        val step = lookAhead.step(puzzle, start(puzzle))

        assertEquals(QueensDeduction.Exclude(HintReason(HintReason.Kind.REGION, 5, lookAhead), setOf(1)), step)
        assertSame(lookAhead, step!!.unit.rung)
        assertEquals("look-ahead", step.unit.rung.id)
        assertEquals(Difficulty.Level.Expert, step.unit.rung.level)
        assertEquals("queens.hint.rung.look-ahead", step.unit.rung.captionKey)
    }

    @Test
    fun `the step is the same across runs`() {
        val puzzle = needsLookAhead()
        val first = lookAhead.step(puzzle, start(puzzle))
        repeat(3) { assertEquals(first, lookAhead.step(puzzle, start(puzzle))) }
        val fresh = LookAheadRung(listOf(SinglesRung)).step(puzzle, start(puzzle)) as QueensDeduction.Exclude
        assertEquals((first as QueensDeduction.Exclude).cells, fresh.cells)
        assertEquals(first.unit.kind to first.unit.unit, fresh.unit.kind to fresh.unit.unit)
    }

    @Test
    fun `the propagation rungs come from the constructor`() {
        val puzzle = needsLookAhead()
        val bare = LookAheadRung(emptyList())
        assertEquals(
            QueensDeduction.Exclude(HintReason(HintReason.Kind.REGION, 2, bare), setOf(22)),
            bare.step(puzzle, start(puzzle)),
        )
    }

    @Test
    fun `a step changes the candidate state`() {
        val puzzle = needsLookAhead()
        val before = start(puzzle)
        val step = lookAhead.step(puzzle, before) as QueensDeduction.Exclude
        val after = before.exclude(step.cells)
        assertTrue(step.cells.all(before::isOpen))
        assertFalse(step.cells.any(after::isOpen))
    }

    @Test
    fun `exclusions chain to the next placement`() {
        val puzzle = needsLookAhead()
        val ladder = QueensLadder(listOf(SinglesRung, lookAhead))
        var candidates = start(puzzle)
        var placement: QueensDeduction.Place? = null
        while (placement == null) {
            when (val step = ladder.next(puzzle, candidates)!!) {
                is QueensDeduction.Place -> placement = step
                is QueensDeduction.Exclude -> candidates = candidates.exclude(step.cells)
            }
        }
        assertTrue(placement.cell in puzzle.solution!!.map { it.idx })
    }

    @Test
    fun `the ladder with look-ahead solves the board and never excludes a solution cell`() {
        val puzzle = needsLookAhead()
        val solution = puzzle.solution!!.map { it.idx }.toSet()
        val ladder = QueensLadder(listOf(SinglesRung, lookAhead))
        var candidates = start(puzzle)
        while (true) {
            candidates = when (val step = ladder.next(puzzle, candidates) ?: break) {
                is QueensDeduction.Place -> candidates.place(puzzle, step.cell)
                is QueensDeduction.Exclude -> {
                    assertTrue(step.cells.none { it in solution })
                    candidates.exclude(step.cells)
                }
            }
        }
        assertEquals(solution, candidates.queens)

        val run = ladder.run(puzzle)
        assertTrue(run.solved)
        assertSame(lookAhead, run.hardest)
    }

    @Test
    fun `the default ladder solves the look-ahead board without look-ahead`() {
        // Line lock settles what singles alone needed a probe for.
        val run = QueensLadder.Default.run(needsLookAhead())
        assertTrue(run.solved)
        assertSame(LineLockRung, run.hardest)
    }
}
