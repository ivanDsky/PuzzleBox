package com.ivandsky.puzzlebox.games.queensui.hint

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
class QueensSpotlightGlyphsTest {

    private fun paint(role: SpotlightRole, culpritIsCross: Boolean = false): List<Int> {
        val side = 90
        val bitmap = ImageBitmap(side, side)
        val size = Size(side.toFloat(), side.toFloat())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size) {
            with(QueensSpotlightGlyphs(culpritIsCross)) { draw(0, role, size, Color.Black) }
        }
        return IntArray(side * side).also { bitmap.readPixels(it) }.toList()
    }

    private fun List<Int>.painted() = count { it ushr 24 != 0 }

    @Test
    fun `a target is a crown and an excluded cell is a cross, and they differ`() {
        val crown = paint(SpotlightRole.TARGET)
        val cross = paint(SpotlightRole.EXCLUDED)

        assertTrue(crown.painted() > 0)
        assertTrue(cross.painted() > 0)
        assertNotEquals(crown, cross)
    }

    @Test
    fun `a culprit draws the board glyph, a crown, or a cross for a wrong cross`() {
        assertEquals(paint(SpotlightRole.TARGET), paint(SpotlightRole.CULPRIT, culpritIsCross = false))
        assertEquals(paint(SpotlightRole.EXCLUDED), paint(SpotlightRole.CULPRIT, culpritIsCross = true))
    }

    @Test
    fun `evidence draws nothing`() {
        assertEquals(0, paint(SpotlightRole.EVIDENCE).painted())
    }
}
