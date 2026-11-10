package com.ivandsky.puzzlebox.games.queensui.hint

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.games.queensui.QueensBoardDefaults
import com.ivandsky.puzzlebox.games.queensui.drawQueen
import com.ivandsky.puzzlebox.games.queensui.drawX
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

    private val side = 90
    private val size = Size(side.toFloat(), side.toFloat())

    /** A white cell with [boardGlyph] drawn in black, then the Spotlight's [role] glyph in red, as the overlay stacks them. */
    private fun paint(
        role: SpotlightRole,
        culpritIsCross: Boolean = false,
        color: Color = Color.Red,
        boardGlyph: (DrawScope.() -> Unit)? = null,
    ): List<Int> {
        val bitmap = ImageBitmap(side, side)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size) {
            drawRect(Color.White)
            boardGlyph?.invoke(this)
            with(QueensSpotlightGlyphs(culpritIsCross)) { draw(0, role, size, color) }
        }
        return IntArray(side * side).also { bitmap.readPixels(it) }.toList()
    }

    private val boardCrown: DrawScope.() -> Unit =
        { drawQueen(Color.Black, QueensBoardDefaults.queenHeightFraction, alpha = 1f, path = Path()) }

    private val boardCross: DrawScope.() -> Unit =
        { drawX(Color.Black, QueensBoardDefaults.crossWeight.toPx(), QueensBoardDefaults.crossLengthFraction) }

    /** Pixels whose red channel dropped: board ink that the red glyph did not cover (white and red both have R = 255). */
    private fun List<Int>.survivingInk() = count { (it shr 16) and 0xFF < 250 }

    private fun List<Int>.red() = count { (it shr 16) and 0xFF >= 250 && (it shr 8) and 0xFF < 250 }

    @Test
    fun `a target is a crown and an excluded cell is a cross, and they differ`() {
        val crown = paint(SpotlightRole.TARGET)
        val cross = paint(SpotlightRole.EXCLUDED)

        assertTrue(crown.red() > 0)
        assertTrue(cross.red() > 0)
        assertNotEquals(crown, cross)
    }

    @Test
    fun `a queen culprit covers the board crown with no ink rim`() {
        val covered = paint(SpotlightRole.CULPRIT, culpritIsCross = false, boardGlyph = boardCrown)

        assertEquals(0, covered.survivingInk())
        assertTrue(covered.red() > paint(SpotlightRole.TARGET).red())
    }

    @Test
    fun `a queen culprit stays opaque under a half-alpha colour`() {
        val pulsing = paint(SpotlightRole.CULPRIT, color = Color.Red.copy(alpha = 0.5f))

        assertEquals(0xFFFF0000.toInt(), pulsing[side / 2 * side + side / 2])
    }

    @Test
    fun `a cross culprit covers the board cross with no ink rim`() {
        val covered = paint(SpotlightRole.CULPRIT, culpritIsCross = true, boardGlyph = boardCross)

        assertEquals(0, covered.survivingInk())
        assertTrue(covered.red() > paint(SpotlightRole.EXCLUDED).red())
    }

    @Test
    fun `evidence draws nothing`() {
        assertEquals(0, paint(SpotlightRole.EVIDENCE).red())
    }
}
