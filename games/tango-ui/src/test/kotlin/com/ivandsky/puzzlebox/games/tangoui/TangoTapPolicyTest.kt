package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Placement interaction policy (design-direction §5, A11 tap-cycle): pure decision
 * table, givens inert, cycle steps and custom actions asserted.
 */
class TangoTapPolicyTest {

    private fun model(cells: Map<Int, TangoCell?>, givens: Set<Int> = emptySet()) =
        TangoRendererModel(
            n = 6,
            cells = (0 until 36).map { idx ->
                TangoRendererModel.CellRender(value = cells[idx], isGiven = idx in givens)
            },
            markers = emptyList(),
            rejection = null,
            status = TangoRendererModel.Status.PLAYING,
        )

    @Test
    fun `cycle steps empty sun moon empty`() {
        val m = model(cells = mapOf(0 to null))
        assertEquals(listOf(TangoPlacement(0, TangoCell.SUN)), TangoTapPolicy.onTap(m, 0))
        assertEquals(listOf(TangoPlacement(0, TangoCell.MOON)), TangoTapPolicy.onTap(model(cells = mapOf(0 to TangoCell.SUN)), 0))
        assertEquals(listOf(TangoPlacement(0, null)), TangoTapPolicy.onTap(model(cells = mapOf(0 to TangoCell.MOON)), 0))
    }

    @Test
    fun `givens are inert`() {
        val m = model(cells = mapOf(0 to TangoCell.SUN), givens = setOf(0))
        assertTrue(TangoTapPolicy.onTap(m, 0).isEmpty())
    }

    // ---- TalkBack custom actions (§9) ----

    /** I4: custom actions commit ONLY the final placement — no spurious transient mistakes. */
    @Test
    fun `custom actions commit the final placement only`() {
        val empty = model(cells = mapOf(4 to null))
        assertEquals(listOf(TangoPlacement(4, TangoCell.SUN)), TangoTapPolicy.onCustomAction(empty, 4, "Place sun"))
        // From empty, "Place moon" lands the moon — the transient sun is never committed.
        assertEquals(listOf(TangoPlacement(4, TangoCell.MOON)), TangoTapPolicy.onCustomAction(empty, 4, "Place moon"))
        // "Clear" on a sun lands only the clear — never the transient moon (the
        // spurious-mistake regression this pins).
        assertEquals(listOf(TangoPlacement(4, null)), TangoTapPolicy.onCustomAction(model(cells = mapOf(4 to TangoCell.SUN)), 4, "Clear"))
        // Already-target: no-op, no commits.
        assertEquals(emptyList<TangoPlacement>(), TangoTapPolicy.onCustomAction(model(cells = mapOf(4 to TangoCell.MOON)), 4, "Place moon"))
    }
}
