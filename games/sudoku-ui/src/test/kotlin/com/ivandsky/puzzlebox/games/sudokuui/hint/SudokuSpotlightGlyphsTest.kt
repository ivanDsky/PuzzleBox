package com.ivandsky.puzzlebox.games.sudokuui.hint

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SudokuSpotlightGlyphsTest {

    private class Pixels(val side: Int, val argb: IntArray) {
        val painted: Int get() = argb.count { it ushr 24 != 0 }

        /** Painted pixels on the cell's middle row. */
        val middleRow: Int get() = (0 until side).count { argb[side / 2 * side + it] ushr 24 != 0 }
    }

    private fun paint(glyphs: SudokuSpotlightGlyphs, cell: Int, role: SpotlightRole): Pixels {
        val side = 90
        val bitmap = ImageBitmap(side, side)
        val size = Size(side.toFloat(), side.toFloat())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size) {
            with(glyphs) { draw(cell, role, size, Color.Black) }
        }
        return Pixels(side, IntArray(side * side).also { bitmap.readPixels(it) })
    }

    @Test
    fun `a target paints its digit and a different digit paints differently`() {
        val glyphs = SudokuSpotlightGlyphs(mapOf(4 to "1", 5 to "8"))

        val one = paint(glyphs, 4, SpotlightRole.TARGET)
        val eight = paint(glyphs, 5, SpotlightRole.TARGET)

        assertTrue(one.painted > 0)
        assertNotEquals(one.argb.toList(), eight.argb.toList())
    }

    @Test
    fun `a culprit redraws the digit heavier than a target`() {
        val glyphs = SudokuSpotlightGlyphs(mapOf(4 to "8"))

        assertTrue(paint(glyphs, 4, SpotlightRole.CULPRIT).painted > paint(glyphs, 4, SpotlightRole.TARGET).painted)
    }

    @Test
    fun `an excluded cell paints its digits with a line through them`() {
        val glyphs = SudokuSpotlightGlyphs(mapOf(4 to "8"))

        val struck = paint(glyphs, 4, SpotlightRole.EXCLUDED)
        val plain = paint(glyphs, 4, SpotlightRole.TARGET)

        assertTrue("the strike adds ink", struck.painted > plain.painted)
        assertTrue("the strike runs across the middle row", struck.middleRow > plain.middleRow)
    }

    @Test
    fun `several struck digits draw smaller than one`() {
        val one = paint(SudokuSpotlightGlyphs(mapOf(0 to "4")), 0, SpotlightRole.EXCLUDED)
        val several = paint(SudokuSpotlightGlyphs(mapOf(0 to "4 5 6")), 0, SpotlightRole.EXCLUDED)

        assertTrue(several.painted > 0)
        assertNotEquals(one.argb.toList(), several.argb.toList())
    }

    @Test
    fun `evidence paints nothing`() {
        assertEquals(0, paint(SudokuSpotlightGlyphs(mapOf(4 to "8")), 4, SpotlightRole.EVIDENCE).painted)
    }

    @Test
    fun `a cell with no text paints nothing in any role`() {
        val glyphs = SudokuSpotlightGlyphs(mapOf(4 to "8"))

        for (role in SpotlightRole.entries) assertEquals("$role", 0, paint(glyphs, 3, role).painted)
    }
}
