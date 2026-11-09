package com.ivandsky.puzzlebox.games.tangoui.hint

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.games.tango.TangoCell
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
class TangoSpotlightGlyphsTest {

    private val glyphs = TangoSpotlightGlyphs(6, mapOf(0 to TangoCell.SUN, 1 to TangoCell.MOON))

    private fun paint(cell: Int, role: SpotlightRole, glyphs: TangoSpotlightGlyphs = this.glyphs): List<Int> {
        val side = 90
        val bitmap = ImageBitmap(side, side)
        val size = Size(side.toFloat(), side.toFloat())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size) {
            with(glyphs) { draw(cell, role, size, Color.Black) }
        }
        return IntArray(side * side).also { bitmap.readPixels(it) }.toList()
    }

    private fun List<Int>.painted() = count { it ushr 24 != 0 }

    @Test
    fun `a target paints the sun or the moon the step wants`() {
        val sun = paint(0, SpotlightRole.TARGET)
        val moon = paint(1, SpotlightRole.TARGET)

        assertTrue(sun.painted() > 0)
        assertTrue(moon.painted() > 0)
        assertNotEquals(sun, moon)
    }

    private fun List<Int>.paintedAt(): Set<Int> = indices.filterTo(HashSet()) { this[it] ushr 24 != 0 }

    @Test
    fun `a culprit paints the placed value past its edge, so it covers the board's own glyph`() {
        for (cell in listOf(0, 1)) {
            val target = paint(cell, SpotlightRole.TARGET).paintedAt()
            val culprit = paint(cell, SpotlightRole.CULPRIT).paintedAt()

            assertEquals("cell $cell: target pixels the culprit leaves bare", emptySet<Int>(), target - culprit)
            assertTrue("cell $cell: the culprit is larger", culprit.size > target.size)
        }
    }

    @Test
    fun `an excluded cell paints a cross that is neither the sun nor the moon, and needs no value`() {
        val cross = paint(5, SpotlightRole.EXCLUDED)

        assertTrue(cross.painted() > 0)
        assertNotEquals(paint(0, SpotlightRole.TARGET), cross)
        assertNotEquals(paint(1, SpotlightRole.TARGET), cross)
        assertEquals("the cross is the same in every cell", cross, paint(0, SpotlightRole.EXCLUDED))
    }

    @Test
    fun `evidence paints nothing`() {
        assertEquals(0, paint(0, SpotlightRole.EVIDENCE).painted())
    }

    @Test
    fun `a cell with no value paints nothing as a target or a culprit`() {
        for (role in listOf(SpotlightRole.TARGET, SpotlightRole.CULPRIT, SpotlightRole.EVIDENCE)) {
            assertEquals("$role", 0, paint(3, role).painted())
        }
    }
}
