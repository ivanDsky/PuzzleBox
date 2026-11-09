package com.ivandsky.puzzlebox.games.tango.engine

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The search settles the hard boards within a node count, never a wall clock (spec §5): one
 * node is one [Budget] poll. The plain backtracking solver took 5 423 825 nodes on the seed-26
 * board and never decided the 8×8 / 10×10 parity traps.
 */
class TangoSolverBudgetTest {

    @Test
    fun `the hard 10x10 seed settles Unique within 250 000 nodes`() {
        val outcome = settle(fixture("n10-hard-seed26.bin"), HARD_BOUND, "n10-hard-seed26")

        assertEquals(1, outcome.solutions.size)
    }

    @Test
    fun `every generated 10x10 corpus board settles within 250 000 nodes`() {
        for (seed in TangoBoardGenerator.CORPUS_SEEDS) {
            val boards = TangoBoardGenerator.boards(10, seed)

            assertEquals("seed $seed minimal", 1, settle(boards.minimal, HARD_BOUND, "seed $seed minimal").solutions.size)
            assertEquals("seed $seed multiple", 2, settle(boards.multiple, HARD_BOUND, "seed $seed multiple").solutions.size)
        }
    }

    @Test
    fun `the 8x8 and 10x10 parity traps settle with no solution within 10 000 nodes`() {
        for (n in listOf(8, 10)) {
            val trap = fixture("n$n-parity-none.bin")
            assertArrayEquals("n$n fixture", TangoCodec.encode(TangoBoardGenerator.parityTrap(n)), TangoCodec.encode(trap))

            assertEquals("n$n", 0, settle(trap, TRAP_BOUND, "n$n").solutions.size)
        }
    }

    /** Runs [TangoUniqueness.bounded] for two solutions on a budget that is spent after [bound] nodes. */
    private fun settle(puzzle: TangoPuzzle, bound: Int, label: String): TangoSearchOutcome.Done {
        var nodes = 0
        val outcome = TangoUniqueness.bounded(puzzle, limit = 2, budget = Budget { ++nodes > bound })
        assertTrue("$label not settled within $bound nodes", outcome is TangoSearchOutcome.Done)
        return outcome as TangoSearchOutcome.Done
    }

    private fun fixture(name: String): TangoPuzzle {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/solver/$name")) { name }.use { it.readBytes() }
        return (TangoCodec.decode(bytes) as DecodeResult.Ok).value
    }

    private companion object {
        const val HARD_BOUND = 250_000
        const val TRAP_BOUND = 10_000
    }
}
