package com.ivandsky.puzzlebox.games.sudokuui.hint

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.AppTypography
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightGlyphs
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SudokuSpotlightGlyphsTest {

    private class Pixels(val side: Int, val argb: IntArray) {
        val painted: Int get() = argb.count { it.isInk() }

        /** Painted pixels on the cell's middle row. */
        val middleRow: Int get() = (0 until side).count { argb[side / 2 * side + it].isInk() }

        fun inked(): Set<Int> = argb.indices.filterTo(HashSet()) { argb[it].isInk() }
    }

    private val measurer = TextMeasurer(createFontFamilyResolver(RuntimeEnvironment.getApplication()), Density(1f), LayoutDirection.Ltr)

    /** The grid's digit style for a [SIDE] px cell: titleLarge (Baloo 2) at 0.6 of the cell, one line box tall. */
    private val digit = AppTypography.titleLarge.copy(
        fontFeatureSettings = "tnum",
        textAlign = TextAlign.Center,
        fontSize = (SIDE * 0.6f).sp,
        lineHeight = (SIDE * 0.6f).sp,
    )

    private fun styled(texts: Map<Int, String>, roles: Map<Int, SpotlightRole>) =
        StyledSudokuGlyphs(texts, roles, measurer, digit, cellPx = SIDE.toFloat())

    private fun paint(glyphs: SpotlightGlyphs, cell: Int, role: SpotlightRole, drawn: Int = SIDE): Pixels {
        val bitmap = ImageBitmap(drawn, drawn)
        val size = Size(drawn.toFloat(), drawn.toFloat())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size) {
            with(glyphs) { draw(cell, role, size, Color.Black) }
        }
        return Pixels(drawn, IntArray(drawn * drawn).also { bitmap.readPixels(it) })
    }

    @Test
    fun `a target paints its digit and a different digit paints differently`() {
        val glyphs = styled(mapOf(4 to "1", 5 to "8"), mapOf(4 to SpotlightRole.TARGET, 5 to SpotlightRole.TARGET))

        val one = paint(glyphs, 4, SpotlightRole.TARGET)
        val eight = paint(glyphs, 5, SpotlightRole.TARGET)

        assertTrue(one.painted > 0)
        assertNotEquals(one.argb.toList(), eight.argb.toList())
    }

    @Test
    fun `a culprit covers the same digit in the board's style`() {
        for (text in listOf("1", "4", "8")) {
            val board = paint(styled(mapOf(4 to text), mapOf(4 to SpotlightRole.TARGET)), 4, SpotlightRole.TARGET)
            val culprit = paint(styled(mapOf(4 to text), mapOf(4 to SpotlightRole.CULPRIT)), 4, SpotlightRole.CULPRIT)

            assertTrue(text, culprit.painted > board.painted)
            // Same font, same centre: only the Bold foot's sharper corners can peek past the ExtraBold's rounder ones.
            val uncovered = (board.inked() - culprit.inked()).size
            assertTrue("$uncovered of ${board.painted} board pixels of $text uncovered", uncovered * 100 <= board.painted * CORNER_PERCENT)
        }
    }

    @Test
    fun `an excluded cell paints its digits with a line through them`() {
        val struck = paint(styled(mapOf(4 to "8"), mapOf(4 to SpotlightRole.EXCLUDED)), 4, SpotlightRole.EXCLUDED)
        val plain = paint(styled(mapOf(4 to "8"), mapOf(4 to SpotlightRole.TARGET)), 4, SpotlightRole.TARGET)

        assertTrue("the strike adds ink", struck.painted > plain.painted)
        assertTrue("the strike runs across the middle row", struck.middleRow > plain.middleRow)
    }

    @Test
    fun `several struck digits draw smaller than one`() {
        val roles = mapOf(0 to SpotlightRole.EXCLUDED)
        val one = paint(styled(mapOf(0 to "4"), roles), 0, SpotlightRole.EXCLUDED)
        val several = paint(styled(mapOf(0 to "4 5 6"), roles), 0, SpotlightRole.EXCLUDED)

        assertTrue(several.painted > 0)
        assertNotEquals(one.argb.toList(), several.argb.toList())
    }

    @Test
    fun `a pulse-scaled cell draws the digit bigger`() {
        val glyphs = styled(mapOf(4 to "8"), mapOf(4 to SpotlightRole.TARGET))

        assertTrue(paint(glyphs, 4, SpotlightRole.TARGET, drawn = SIDE * 6 / 5).painted > paint(glyphs, 4, SpotlightRole.TARGET).painted)
    }

    @Test
    fun `evidence, a cell with no text and a role the cell does not have paint nothing`() {
        assertEquals(0, paint(styled(mapOf(4 to "8"), mapOf(4 to SpotlightRole.EVIDENCE)), 4, SpotlightRole.EVIDENCE).painted)
        val glyphs = styled(mapOf(4 to "8"), mapOf(3 to SpotlightRole.TARGET, 4 to SpotlightRole.TARGET))
        for (role in SpotlightRole.entries) assertEquals("$role", 0, paint(glyphs, 3, role).painted)
        assertEquals(0, paint(glyphs, 4, SpotlightRole.CULPRIT).painted)
    }

    @Test
    fun `the unstyled texts draw nothing until the grid styles them`() {
        val texts = SudokuSpotlightGlyphs(mapOf(4 to "8"))
        val spotlight = Spotlight(6, 6, mapOf(4 to SpotlightRole.TARGET), callout = null, texts, playId = 1)

        assertEquals(0, paint(texts, 4, SpotlightRole.TARGET).painted)
        val shown = spotlight.withDigitStyle(measurer, digit, SIDE.toFloat())
        assertEquals(spotlight.copy(glyphs = styled(mapOf(4 to "8"), spotlight.roles)), shown)
        assertTrue(paint(shown.glyphs, 4, SpotlightRole.TARGET).painted > 0)
    }

    @Test
    fun `another game's glyphs pass through unstyled`() {
        val other = SpotlightGlyphs { _, _, _, _ -> }
        val spotlight = Spotlight(6, 6, mapOf(4 to SpotlightRole.TARGET), callout = null, other, playId = 1)

        assertSame(spotlight, spotlight.withDigitStyle(measurer, digit, SIDE.toFloat()))
    }

    private companion object {
        const val SIDE = 90
        /** Corner fringe allowed at a 90 px cell; a font or centre mismatch leaves a whole stroke uncovered. */
        const val CORNER_PERCENT = 2
    }
}

private fun Int.isInk(): Boolean = this ushr 24 > INK_ALPHA

/** Above anti-aliasing fringe: a pixel counts once it is at least half covered. */
private const val INK_ALPHA = 127
