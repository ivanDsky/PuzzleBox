package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensTestSupport
import org.junit.Assert.assertEquals
import org.junit.Test

/** [SinglesRung.cells]: the queens `steps` would place, in `steps` order, without building any evidence. */
class SinglesRungCellsTest {

    /**
     * 4×4 [QueensTestSupport.n4Regions] with 5, 6, 7, 15, 9, 10, 12, 13 excluded: the singles are
     * 4 (row 1), 14 (row 3, and region 3), 1 (column 1), 11 (region 1) and 8 (region 2).
     * Region order puts 11 before 8, and 14 stands once although two units name it.
     */
    @Test
    fun `cells come in region then cell order, once each`() {
        val puzzle = QueensTestSupport.n4Puzzle()
        val state = QueensCandidates(4, emptySet(), setOf(5, 6, 7, 15, 9, 10, 12, 13))
        assertEquals(listOf(1, 4, 11, 8, 14), SinglesRung.cells(puzzle, state))
    }

    @Test
    fun `cells equal the places steps would stand at every state of a solve`() {
        val boards = listOf(QueensTestSupport.n4Puzzle(), QueensTestSupport.n8N2Puzzle())
        for (puzzle in boards) {
            for (state in states(puzzle)) {
                val places = SinglesRung.steps(puzzle, state).map { (it.deduction as QueensDeduction.Place).cell }.distinct().toList()
                assertEquals(places, SinglesRung.cells(puzzle, state))
            }
        }
    }

    /** Every state a Default-ladder solve passes through, from the empty board. */
    private fun states(puzzle: QueensPuzzle): List<QueensCandidates> {
        var state = QueensCandidates.start(puzzle, emptySet())
        val seen = mutableListOf(state)
        while (true) {
            state = when (val d = QueensLadder.Default.next(puzzle, state) ?: return seen) {
                is QueensDeduction.Place -> state.place(puzzle, d.cell)
                is QueensDeduction.Exclude -> state.exclude(d.cells)
            }
            seen += state
        }
    }
}
