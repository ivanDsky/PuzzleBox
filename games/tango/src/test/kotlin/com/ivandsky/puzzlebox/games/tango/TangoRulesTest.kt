package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.games.tango.engine.TangoHint
import com.ivandsky.puzzlebox.games.tango.engine.TangoMove
import com.ivandsky.puzzlebox.games.tango.engine.TangoRules
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The three constraint classes at move time (A3, spec success criterion 4). */
class TangoRulesTest {

    private fun full(vararg rows: String): TangoState =
        TangoState(
            TangoTestSupport.sample,
            rows.flatMap { r -> r.map { if (it == 'S') TangoCell.SUN else TangoCell.MOON } },
        )

    private fun rejectReason(verdict: TangoVerdict): TangoVerdict.Reason? =
        (verdict as? TangoVerdict.Rejected)?.reason

    @Test
    fun `placing on a given is rejected as locked`() {
        val verdict = TangoRules.validate(TangoMove(0, TangoCell.MOON), TangoState(TangoTestSupport.sample, TangoTestSupport.gridOf()))
        assertEquals(TangoVerdict.Reason.LockedGiven, rejectReason(verdict))
    }

    @Test
    fun `a foreign move is rejected, never thrown on`() {
        val verdict = TangoRules.validate(object : com.ivandsky.puzzlebox.game.Move {}, TangoState(TangoTestSupport.sample, TangoTestSupport.gridOf()))
        assertEquals(TangoVerdict.Reason.ForeignMove, rejectReason(verdict))
    }

    @Test
    fun `a legal first placement is valid`() {
        val verdict = TangoRules.validate(TangoMove(2, TangoCell.MOON), TangoState(TangoTestSupport.sample, TangoTestSupport.gridOf()))
        assertEquals(TangoVerdict.Valid, verdict)
    }

    @Test
    fun `clearing a player cell is always valid`() {
        val state = TangoState(TangoTestSupport.sample, TangoTestSupport.gridOf().toMutableList().also { it[2] = TangoCell.MOON })
        assertEquals(TangoVerdict.Valid, TangoRules.validate(TangoMove(2, null), state))
    }

    @Test
    fun `three identical in a row is rejected`() {
        // row 1 has givens SUN at (1,0),(1,1); placing SUN at (1,2) builds S S S.
        val state = TangoState(TangoTestSupport.sample, TangoTestSupport.gridOf())
        val verdict = TangoRules.validate(TangoMove(8, TangoCell.SUN), state)
        assertEquals(TangoVerdict.Reason.ThreeInARow, rejectReason(verdict))
    }

    @Test
    fun `three identical in a column is rejected`() {
        // column 0 starts with givens S at (0,0),(1,0); placing SUN at (2,0) builds S S S.
        val grid = TangoTestSupport.gridOf()
        val state = TangoState(TangoTestSupport.sample, grid)
        val verdict = TangoRules.validate(TangoMove(12, TangoCell.SUN), state)
        assertEquals(TangoVerdict.Reason.ThreeInARow, rejectReason(verdict))
    }

    @Test
    fun `placing a fourth sun in a row is rejected as count exceeded`() {
        // row 0 has givens S S at (0,0),(0,1); a fourth sun anywhere in row 0 exceeds, with no triple in between.
        val grid = TangoTestSupport.gridOf().toMutableList().also { it[3] = TangoCell.SUN }
        val state = TangoState(TangoTestSupport.sample, grid)
        val verdict = TangoRules.validate(TangoMove(4, TangoCell.SUN), state)
        assertEquals(TangoVerdict.Reason.CountExceeded, rejectReason(verdict))
    }

    @Test
    fun `violating a same marker is rejected`() {
        // marker (18,RIGHT,'='): cells 18 and 19 must be equal; force 19 to MOON after 18 is SUN.
        val grid = TangoTestSupport.gridOf().toMutableList().also { it[18] = TangoCell.SUN }
        val state = TangoState(TangoTestSupport.sample, grid)
        val verdict = TangoRules.validate(TangoMove(19, TangoCell.MOON), state)
        assertEquals(TangoVerdict.Reason.MarkerConflict, rejectReason(verdict))
    }

    @Test
    fun `violating an opposite marker is rejected`() {
        // marker (18,DOWN,'×'): cells 18 and 24 must differ; force both SUN.
        val grid = TangoTestSupport.gridOf().toMutableList().also { it[18] = TangoCell.SUN }
        val state = TangoState(TangoTestSupport.sample, grid)
        val verdict = TangoRules.validate(TangoMove(24, TangoCell.SUN), state)
        assertEquals(TangoVerdict.Reason.MarkerConflict, rejectReason(verdict))
    }

    @Test
    fun `satisfying a marker is valid`() {
        val grid = TangoTestSupport.gridOf().toMutableList().also { it[18] = TangoCell.SUN }
        val state = TangoState(TangoTestSupport.sample, grid)
        assertEquals(TangoVerdict.Valid, TangoRules.validate(TangoMove(19, TangoCell.SUN), state))
    }

    @Test
    fun `the sample solution is complete`() {
        val state = full("SSMSMM", "SSMSMM", "MMSMSS", "SSMMSM", "MMSSMS", "MMSMSS")
        assertTrue(TangoRules.isComplete(state))
    }

    @Test
    fun `an incomplete grid is not complete even when all constraints locally hold`() {
        val grid = TangoTestSupport.gridOf().toMutableList().also { it[2] = TangoCell.MOON }
        assertFalse(TangoRules.isComplete(TangoState(TangoTestSupport.sample, grid)))
    }

    @Test
    fun `a full grid violating counts is not complete`() {
        // swap two cells of the solution in row 0: sun/moon counts break.
        val state = full("SMSMSM", "SSMSMM", "MMSMSS", "SSMMSM", "MMSSMS", "MMSMSS")
        assertFalse(TangoRules.isComplete(state))
    }

    @Test
    fun `a full grid with a triple is not complete`() {
        val state = full("SSMSMM", "SSMSMM", "MMSMSS", "SSMMSM", "MMSSMS", "SSMSMM")
        assertFalse(TangoRules.isComplete(state))
    }

    @Test
    fun `the empty sample board opens the ladder with a three-in-a-row deduction`() {
        val state = TangoState(TangoTestSupport.sample, TangoTestSupport.gridOf())
        assertEquals(
            TangoHint.Forced(2, TangoCell.MOON, TangoHint.Why.ThreeInARow(0, 1)),
            TangoRules.hint(state),
        )
    }

    @Test
    fun `a board with no deduction at all hints stuck`() {
        val blank = TangoPuzzle(TangoTestSupport.variant, n = 2, givens = emptyMap(), markers = emptyList())
        assertEquals(TangoHint.Stuck, TangoRules.hint(TangoState(blank, List(4) { null })))
    }
}
