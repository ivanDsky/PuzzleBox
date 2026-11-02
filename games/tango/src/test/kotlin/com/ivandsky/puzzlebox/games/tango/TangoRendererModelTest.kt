package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.games.tango.engine.TangoHint
import com.ivandsky.puzzlebox.games.tango.engine.TangoMove
import com.ivandsky.puzzlebox.games.tango.engine.TangoRules
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The state -> model projection: no Compose, pure JVM. */
class TangoRendererModelTest {

    @Test
    fun `empty grid renders IDLE with marker decorations`() {
        val model = TangoRendererModel.of(TangoState(TangoTestSupport.sample, TangoTestSupport.gridOf()), null)
        assertEquals(TangoRendererModel.Status.IDLE, model.status)
        assertEquals(4, model.markers.size)
        assertEquals(36, model.cells.size)
        assertEquals(10, model.cells.count { it.isGiven })
    }

    @Test
    fun `a rejected verdict projects onto the offending cell`() {
        val state = TangoState(TangoTestSupport.sample, TangoTestSupport.gridOf())
        val verdict = TangoRules.validate(TangoMove(12, TangoCell.SUN), state)
        val model = TangoRendererModel.of(state, null, verdict)
        assertEquals(TangoVerdict.Reason.ThreeInARow, model.rejection?.reason)
        assertEquals(12, model.rejection?.idx)
        // Nothing placed yet — the board is idle even while showing the rejection.
        assertEquals(TangoRendererModel.Status.IDLE, model.status)
    }

    @Test
    fun `the completed grid renders COMPLETE with no rejection`() {
        val state = TangoState(TangoTestSupport.sample, TangoTestSupport.solution)
        val model = TangoRendererModel.of(state, null)
        assertEquals(TangoRendererModel.Status.COMPLETE, model.status)
        assertNull(model.rejection)
        assertTrue(model.cells.all { it.value != null })
    }
}
