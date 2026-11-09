package com.ivandsky.puzzlebox.designsystem.spotlight

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure Spotlight geometry: cell rects, the lit set, its hairline, and where the callout goes. */
class SpotlightLayoutTest {

    private val glyphs = SpotlightGlyphs { _, _, _, _ -> }

    private fun spotlight(rows: Int, cols: Int, roles: Map<Int, SpotlightRole>, anchor: Set<Int>? = null) =
        Spotlight(rows, cols, roles, anchor?.let { SpotlightCallout("Step: text.", it) }, glyphs, playId = 0)

    private fun Spotlight.callout(size: Size, height: Float): Pair<CalloutPlacement, Rect> {
        val placement = checkNotNull(calloutPlacement(size, height))
        return placement to Rect(0f, placement.top, size.width, placement.top + height)
    }

    private fun Spotlight.assertCoversNoLitCell(size: Size, callout: Rect) {
        for (cell in litCells()) {
            assertFalse("callout $callout covers lit cell $cell", callout.overlaps(cellRect(cell, rows, cols, size)))
        }
    }

    @Test
    fun `a cell's rect is its column and row share of the bounds`() {
        val size = Size(500f, 300f)
        assertEquals(Rect(0f, 0f, 100f, 100f), cellRect(0, rows = 3, cols = 5, size))
        assertEquals(Rect(300f, 100f, 400f, 200f), cellRect(8, rows = 3, cols = 5, size))
        assertEquals(Rect(400f, 200f, 500f, 300f), cellRect(14, rows = 3, cols = 5, size))
    }

    @Test
    fun `the lit set is every listed cell on the board`() {
        val roles = mapOf(0 to SpotlightRole.EVIDENCE, 5 to SpotlightRole.EXCLUDED, 7 to SpotlightRole.TARGET, 99 to SpotlightRole.EVIDENCE)
        assertEquals(setOf(0, 5, 7), spotlight(3, 3, roles).litCells())
    }

    @Test
    fun `a 2x2 lit block is traced by its 8 outer unit edges and none inside`() {
        val block = listOf(5, 6, 9, 10).associateWith { SpotlightRole.EVIDENCE }
        val edges = spotlight(4, 4, block).hairlineEdges().toSet()
        val expected = setOf(
            GridEdge(1, 1, 2, 1), GridEdge(2, 1, 3, 1), // top
            GridEdge(1, 3, 2, 3), GridEdge(2, 3, 3, 3), // bottom
            GridEdge(1, 1, 1, 2), GridEdge(1, 2, 1, 3), // left
            GridEdge(3, 1, 3, 2), GridEdge(3, 2, 3, 3), // right
        )
        assertEquals(expected, edges)
    }

    @Test
    fun `a culprit step has no hairline and a deduction step has one`() {
        assertEquals(emptyList<GridEdge>(), spotlight(3, 3, mapOf(4 to SpotlightRole.CULPRIT, 3 to SpotlightRole.EVIDENCE)).hairlineEdges())
        assertEquals(4, spotlight(3, 3, mapOf(4 to SpotlightRole.TARGET)).hairlineEdges().size)
    }

    @Test
    fun `the callout takes the free band beside its anchor on the roomier side and points at the anchor's edge`() {
        // 6x6, 100px cells; the evidence row is row 2: two free rows above, three below.
        val size = Size(600f, 600f)
        val row = setOf(12, 13, 14)
        val step = spotlight(6, 6, row.associateWith { SpotlightRole.EVIDENCE } + (15 to SpotlightRole.TARGET), anchor = row)
        val (placement, rect) = step.callout(size, height = 150f)
        assertEquals(CalloutSide.BELOW, placement.side)
        assertEquals(300f, placement.top) // pointer tip on the anchor's bottom edge
        assertEquals(150f, placement.anchorX) // centre of anchor columns 0..2
        step.assertCoversNoLitCell(size, rect)
    }

    @Test
    fun `the callout sits above its anchor when that side has more room, tip on the anchor's top edge`() {
        val size = Size(600f, 600f)
        val step = spotlight(6, 6, mapOf(26 to SpotlightRole.TARGET, 27 to SpotlightRole.EVIDENCE), anchor = setOf(27))
        val (placement, rect) = step.callout(size, height = 150f)
        assertEquals(CalloutSide.ABOVE, placement.side)
        assertEquals(400f, placement.top + 150f)
        assertEquals(350f, placement.anchorX)
        step.assertCoversNoLitCell(size, rect)
    }

    @Test
    fun `of the free bands the callout picks the one nearest its anchor`() {
        // Lit rows 0 and 5, anchor on row 0: the free band right under row 0 wins over the one over row 5.
        val size = Size(600f, 600f)
        val roles = mapOf(1 to SpotlightRole.EVIDENCE, 2 to SpotlightRole.TARGET, 33 to SpotlightRole.EVIDENCE)
        val (placement, rect) = spotlight(6, 6, roles, anchor = setOf(1)).callout(size, height = 150f)
        assertEquals(CalloutSide.BELOW, placement.side)
        assertEquals(100f, placement.top)
        spotlight(6, 6, roles, anchor = setOf(1)).assertCoversNoLitCell(size, rect)
        // The same lit cells anchored on row 5 put it right over row 5.
        val (low, lowRect) = spotlight(6, 6, roles, anchor = setOf(33)).callout(size, height = 150f)
        assertEquals(CalloutSide.ABOVE, low.side)
        assertEquals(500f, low.top + 150f)
        spotlight(6, 6, roles, anchor = setOf(33)).assertCoversNoLitCell(size, lowRect)
    }

    @Test
    fun `with no free band a 3x3 sandwich puts the callout over excluded cells, never over evidence or target`() {
        // Row 0: one excluded cell; row 1: evidence, target, evidence; row 2: one evidence cell. Every band is lit.
        val size = Size(300f, 300f)
        val roles = mapOf(
            1 to SpotlightRole.EXCLUDED,
            3 to SpotlightRole.EVIDENCE, 4 to SpotlightRole.TARGET, 5 to SpotlightRole.EVIDENCE,
            7 to SpotlightRole.EVIDENCE,
        )
        val (placement, rect) = spotlight(3, 3, roles, anchor = setOf(3, 5)).callout(size, height = 90f)
        assertEquals(CalloutSide.ABOVE, placement.side)
        for (cell in listOf(3, 4, 5, 7)) assertFalse("covers $cell", rect.overlaps(cellRect(cell, 3, 3, size)))
        assertTrue(rect.overlaps(cellRect(1, 3, 3, size)))
    }

    @Test
    fun `degenerate boards draw nothing and never crash`() {
        val size = Size(300f, 300f)
        val empty = spotlight(3, 3, emptyMap(), anchor = setOf(0))
        assertTrue(empty.litCells().isEmpty())
        assertTrue(empty.hairlineEdges().isEmpty())
        assertNull(empty.calloutPlacement(size, 90f))
        for ((rows, cols) in listOf(0 to 3, 3 to 0, 0 to 0)) {
            val none = spotlight(rows, cols, mapOf(0 to SpotlightRole.TARGET), anchor = setOf(0))
            assertTrue(none.litCells().isEmpty())
            assertTrue(none.hairlineEdges().isEmpty())
            assertNull(none.calloutPlacement(size, 90f))
        }
    }

    @Test
    fun `a one-cell board and a 1xN board keep the callout on the board`() {
        val one = spotlight(1, 1, mapOf(0 to SpotlightRole.TARGET), anchor = setOf(0))
        assertEquals(4, one.hairlineEdges().size)
        val onePlacement = one.calloutPlacement(Size(100f, 100f), 60f)!!
        assertEquals(CalloutSide.OVER, onePlacement.side)
        assertTrue(onePlacement.top in 0f..40f)

        val strip = spotlight(1, 5, mapOf(2 to SpotlightRole.TARGET, 1 to SpotlightRole.EVIDENCE), anchor = setOf(1))
        assertEquals(6, strip.hairlineEdges().size)
        val stripPlacement = strip.calloutPlacement(Size(500f, 100f), 140f)!!
        assertEquals(CalloutSide.OVER, stripPlacement.side)
        assertEquals(0f, stripPlacement.top)
    }
}
