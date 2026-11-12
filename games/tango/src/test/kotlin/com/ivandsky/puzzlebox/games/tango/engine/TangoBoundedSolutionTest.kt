package com.ivandsky.puzzlebox.games.tango.engine

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The one solution of a puzzle within a poll budget: the published solution is found, an ambiguous board and an
 * over-budget search both give null, and the same budget gives the same answer every run.
 */
class TangoBoundedSolutionTest {

    @Test
    fun `the default search finds the published solution`() {
        assertEquals(TangoTestSupport.solution, TangoBoundedSolution.of(TangoTestSupport.sample))
    }

    @Test
    fun `an ambiguous board has no solution`() {
        assertNull(TangoBoundedSolution.of(twoSolutions))
    }

    @Test
    fun `an over-budget search gives no solution the same way every run`() {
        repeat(3) { assertNull(TangoBoundedSolution.of(TangoTestSupport.sample, pollLimit = 0)) }
    }

    private companion object {
        /** Rows 0–1 given; row 2 pinned to S _ _ M, so it is SSMM or SMSM and row 3 its complement. */
        val twoSolutions = TangoPuzzle(
            variant = TangoTestSupport.variant,
            n = 4,
            givens = mapOf(
                0 to TangoCell.SUN, 1 to TangoCell.MOON, 2 to TangoCell.SUN, 3 to TangoCell.MOON,
                4 to TangoCell.MOON, 5 to TangoCell.SUN, 6 to TangoCell.MOON, 7 to TangoCell.SUN,
                8 to TangoCell.SUN, 11 to TangoCell.MOON,
            ),
            markers = emptyList(),
        )
    }
}
