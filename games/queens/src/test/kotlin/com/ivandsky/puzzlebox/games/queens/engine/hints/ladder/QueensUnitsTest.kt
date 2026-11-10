package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.games.queens.QueensTestSupport
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.HintReason.Kind
import org.junit.Assert.assertTrue
import org.junit.Test

/** The kind-indexed view of the unit table the rungs read. */
class QueensUnitsTest {

    private val puzzle = QueensTestSupport.n4Puzzle()

    @Test
    fun `a cell sits in the unit of each kind that indexOf names`() {
        for (kind in Kind.entries) {
            for (cell in 0 until puzzle.cellCount) {
                assertTrue("$kind cell $cell", cell in QueensUnits.cells(puzzle, kind)[QueensUnits.indexOf(puzzle, kind, cell)])
            }
        }
    }
}
