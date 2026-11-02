package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Placement interaction policy (design-direction §5, A11 both variants as equals):
 * pure decision table, givens inert, cycle steps and chooser flow asserted.
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

    // ---- Variant A: tap-cycle ----

    @Test
    fun `cycle steps empty sun moon empty`() {
        val policy = TangoTapPolicy(TangoPlacementVariant.TapCycle)
        val m = model(cells = mapOf(0 to null))
        assertEquals(listOf(TangoPlacement(0, TangoCell.SUN)), policy.onTap(m, 0))
        assertEquals(listOf(TangoPlacement(0, TangoCell.MOON)), policy.onTap(model(cells = mapOf(0 to TangoCell.SUN)), 0))
        assertEquals(listOf(TangoPlacement(0, null)), policy.onTap(model(cells = mapOf(0 to TangoCell.MOON)), 0))
    }

    @Test
    fun `givens are inert in both variants`() {
        val m = model(cells = mapOf(0 to TangoCell.SUN), givens = setOf(0))
        assertTrue(TangoTapPolicy(TangoPlacementVariant.TapCycle).onTap(m, 0).isEmpty())
        val b = TangoTapPolicy(TangoPlacementVariant.TwoTapChooser)
        assertTrue(b.onTap(m, 0).isEmpty())
        assertNull(b.pendingIdx)
    }

    // ---- Variant B: two-tap chooser ----

    @Test
    fun `empty tap opens chooser then target stamps the value`() {
        val policy = TangoTapPolicy(TangoPlacementVariant.TwoTapChooser)
        val m = model(cells = mapOf(7 to null))
        assertTrue(policy.onTap(m, 7).isEmpty())
        assertEquals(7, policy.pendingIdx)
        assertEquals(TangoPlacement(7, TangoCell.MOON), policy.chooserChoose(7, TangoCell.MOON))
        assertNull(policy.pendingIdx)
    }

    @Test
    fun `filled tap clears and outside tap dismisses the chooser`() {
        val policy = TangoTapPolicy(TangoPlacementVariant.TwoTapChooser)
        val filled = model(cells = mapOf(7 to TangoCell.SUN))
        assertEquals(listOf(TangoPlacement(7, null)), policy.onTap(filled, 7))

        val empty = model(cells = mapOf(7 to null))
        policy.onTap(empty, 7)
        assertTrue(policy.onTap(empty, 20).isEmpty()) // outside-tap dismiss
        assertNull(policy.pendingIdx)
    }

    // ---- TalkBack custom actions (§9) ----

    @Test
    fun `variant a custom actions map to cycle steps`() {
        val policy = TangoTapPolicy(TangoPlacementVariant.TapCycle)
        val empty = model(cells = mapOf(4 to null))
        assertEquals(listOf(TangoPlacement(4, TangoCell.SUN)), policy.onCustomAction(empty, 4, "Place sun"))
        // From empty, "Place moon" cycles through sun first (two cycle steps).
        assertEquals(
            listOf(
                TangoPlacement(4, TangoCell.SUN),
                TangoPlacement(4, TangoCell.MOON),
            ),
            policy.onCustomAction(empty, 4, "Place moon"),
        )
        // "Clear" on a sun cycles twice (sun → moon → empty).
        assertEquals(
            listOf(
                TangoPlacement(4, TangoCell.MOON),
                TangoPlacement(4, null),
            ),
            policy.onCustomAction(model(cells = mapOf(4 to TangoCell.SUN)), 4, "Clear"),
        )
    }

    @Test
    fun `variant b custom actions stamp directly`() {
        val policy = TangoTapPolicy(TangoPlacementVariant.TwoTapChooser)
        val empty = model(cells = mapOf(4 to null))
        assertEquals(listOf(TangoPlacement(4, TangoCell.MOON)), policy.onCustomAction(empty, 4, "Place moon"))
        assertEquals(listOf(TangoPlacement(4, null)), policy.onCustomAction(model(cells = mapOf(4 to TangoCell.SUN)), 4, "Clear"))
        assertEquals(listOf(TangoPlacement(4, TangoCell.SUN)), policy.onCustomAction(empty, 4, "Place sun"))
    }
}
