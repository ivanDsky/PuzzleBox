package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The pigeonhole rung (spec A9, ticket 07): k ≥ 2 regions confined to k rows (or columns)
 * exclude every other region's cells there; dually k rows (or columns) confined to k regions
 * exclude those regions' cells elsewhere. Crafted boards: no committed daily isolates it.
 */
class PigeonholeRungTest {

    /**
     * 5×5: regions 0 and 1 fill rows 0–1 except column 4, which region 2 (cells 4, 9, 14)
     * runs down. Two regions in two rows → region 2's cells 4 and 9 are excluded.
     */
    private fun twoRegionsInTwoRows(): QueensPuzzle = QueensPuzzle(
        size = 5,
        regions = listOf(
            0, 0, 1, 1, 2,
            0, 0, 1, 1, 2,
            3, 3, 4, 4, 2,
            3, 3, 4, 4, 4,
            3, 3, 4, 4, 4,
        ),
    )

    /**
     * 5×5 dual: rows 0–1 hold only regions 0 and 1, which also reach into row 2 (cells 10
     * and 14). Two rows in two regions → cells 10 and 14 are excluded. No two regions
     * share just two rows, so only the dual fires.
     */
    private fun twoRowsInTwoRegions(): QueensPuzzle = QueensPuzzle(
        size = 5,
        regions = listOf(
            0, 0, 0, 1, 1,
            0, 0, 0, 1, 1,
            0, 2, 2, 4, 1,
            3, 3, 2, 4, 4,
            3, 3, 2, 4, 4,
        ),
    )

    private fun empty(puzzle: QueensPuzzle) = QueensCandidates.start(puzzle, emptySet())

    @Test
    fun `two regions confined to two rows exclude the other region's cells in those rows`() {
        val puzzle = twoRegionsInTwoRows()
        val step = PigeonholeRung.step(puzzle, empty(puzzle))
        assertEquals(QueensDeduction.Exclude(HintReason(HintReason.Kind.ROW, 0, PigeonholeRung), setOf(4, 9)), step)
        assertEquals("pigeonhole", step!!.unit.rung.id)
        assertEquals(Difficulty.Level.Hard, step.unit.rung.level)
        assertEquals("queens.hint.rung.pigeonhole", step.unit.rung.captionKey)
    }

    @Test
    fun `two rows confined to two regions exclude those regions' cells elsewhere`() {
        val puzzle = twoRowsInTwoRegions()
        val step = PigeonholeRung.step(puzzle, empty(puzzle))
        assertEquals(QueensDeduction.Exclude(HintReason(HintReason.Kind.REGION, 0, PigeonholeRung), setOf(10, 14)), step)
    }

    @Test
    fun `a board with no confinement yields null`() {
        val puzzle = QueensTestSupport.n4OpenPuzzle()
        assertNull(PigeonholeRung.step(puzzle, empty(puzzle)))
    }

    @Test
    fun `an applied step changes the state and is never repeated`() {
        // After 4 and 9 go, rows 3–4 (regions 3 and 4 only) are the next confinement.
        val puzzle = twoRegionsInTwoRows()
        val before = empty(puzzle)
        val step = PigeonholeRung.step(puzzle, before) as QueensDeduction.Exclude
        val after = before.exclude(step.cells)
        assertNotEquals(before, after)
        assertEquals(
            QueensDeduction.Exclude(HintReason(HintReason.Kind.REGION, 3, PigeonholeRung), setOf(10, 11, 12, 13)),
            PigeonholeRung.step(puzzle, after),
        )
    }

    @Test
    fun `the exclusion chains to the next placement`() {
        val puzzle = twoRegionsInTwoRows()
        val ladder = QueensLadder(listOf(SinglesRung, PigeonholeRung))
        val first = ladder.next(puzzle, empty(puzzle)) as QueensDeduction.Exclude
        assertEquals(PigeonholeRung, first.unit.rung)
        val second = ladder.next(puzzle, empty(puzzle).exclude(first.cells))
        assertEquals(QueensDeduction.Place(HintReason(HintReason.Kind.REGION, 2), 14), second)
    }
}
