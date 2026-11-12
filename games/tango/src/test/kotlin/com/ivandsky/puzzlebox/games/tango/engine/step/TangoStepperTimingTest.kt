package com.ivandsky.puzzlebox.games.tango.engine.step

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.engine.TangoSearchOutcome
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hang bound for Tango's stepwise hints (spec SC7): one stepper press on a bare 8×8 and a
 * bare 10×10, and the poll-bounded search the UI runs on the hardest 10×10 fixture, each finish
 * under 2 s once the JIT is warm.
 */
class TangoStepperTimingTest {

    @Test
    fun `the stepper on a bare 8x8 board answers within 2 s`() {
        assertStepperFast(bare(8))
    }

    @Test
    fun `the stepper on a bare 10x10 board answers within 2 s`() {
        assertStepperFast(bare(10))
    }

    @Test
    fun `the bounded search on the hardest 10x10 fixture settles within 2 s`() {
        val hardest = fixture("n10-hard-seed26.bin")
        search(hardest)

        val (outcome, ms) = timed { search(hardest) }

        assertEquals(1, (outcome as TangoSearchOutcome.Done).solutions.size)
        assertTrue("took $ms ms", ms < LIMIT_MS)
    }

    private fun assertStepperFast(puzzle: TangoPuzzle) {
        val grid = List(puzzle.cellCount) { puzzle.givens[it] }
        TangoStepper.next(puzzle, grid, lastMove = null, solution = null)

        val (_, ms) = timed { TangoStepper.next(puzzle, grid, lastMove = null, solution = null) }

        assertTrue("n=${puzzle.n} took $ms ms", ms < LIMIT_MS)
    }

    /**
     * The search the UI runs: two solutions wanted, spent after [POLL_LIMIT] nodes
     * (TangoBoundedSolution.POLL_LIMIT).
     */
    private fun search(puzzle: TangoPuzzle): TangoSearchOutcome {
        var polls = 0
        return TangoUniqueness.bounded(puzzle, limit = 2, budget = Budget { polls++ >= POLL_LIMIT })
    }

    private fun <T> timed(block: () -> T): Pair<T, Long> {
        val start = System.nanoTime()
        val value = block()
        return value to (System.nanoTime() - start) / NANOS_PER_MILLI
    }

    private fun bare(n: Int) = TangoPuzzle(VariantId("tango/n$n"), n, emptyMap(), emptyList())

    private fun fixture(name: String): TangoPuzzle {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/solver/$name")) { name }.use { it.readBytes() }
        return (TangoCodec.decode(bytes) as DecodeResult.Ok).value
    }

    private companion object {
        const val LIMIT_MS = 2_000L
        const val POLL_LIMIT = 100_000
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
