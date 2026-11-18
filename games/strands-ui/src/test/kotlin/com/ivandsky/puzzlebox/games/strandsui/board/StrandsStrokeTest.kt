package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.ivandsky.puzzlebox.designsystem.DarkBoardTokens
import com.ivandsky.puzzlebox.designsystem.LightBoardTokens
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.games.strands.render.LinkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stroke widths keep their named size from a 48 dp cell up and thin in proportion below it. */
class StrandsStrokeTest {

    private val density = Density(2.5f)

    private fun px(dp: Float) = dp * density.density

    @Test
    fun `at and above the stroke pitch a width keeps its named size`() {
        for (cell in listOf(48f, 55f, 72f, 96f)) {
            assertEquals("2 dp on a $cell dp cell", px(2f), with(density) { strandsStroke(2.dp, px(cell)) }, 1e-4f)
        }
    }

    @Test
    fun `below the stroke pitch a width thins in proportion to the cell`() {
        assertEquals(px(1f), with(density) { strandsStroke(2.dp, px(24f)) }, 1e-4f)
        assertEquals(px(4.5f * 14.62f / 48f), with(density) { strandsStroke(4.5.dp, px(14.62f)) }, 1e-4f)
    }

    @Test
    fun `a token's stroke scales with the cell the token sits in`() {
        val bigToken = px(96f) * StrandsBoardSpec.TokenFraction / 2
        val smallToken = px(12f) * StrandsBoardSpec.TokenFraction / 2
        assertEquals(px(3f), with(density) { tokenStroke(3.dp, bigToken) }, 1e-3f)
        assertEquals(px(3f / 4), with(density) { tokenStroke(3.dp, smallToken) }, 1e-3f)
    }

    @Test
    fun `connectors are a fraction of the cell pitch, whatever the size`() {
        for (pitch in listOf(24f, 48f, 60f, 96f)) {
            assertEquals(pitch * StrandsOutlinedSpec.SpangramConnector, connectorWidth(LinkKind.Spangram, pitch), 1e-4f)
            assertEquals(pitch * StrandsOutlinedSpec.ThemeConnector, connectorWidth(LinkKind.Theme, pitch), 1e-4f)
            assertEquals(pitch * StrandsOutlinedSpec.ThinConnector, connectorWidth(LinkKind.Trace, pitch), 1e-4f)
        }
    }

    @Test
    fun `play never thins a stroke - its floor cell is at least the stroke pitch`() {
        assertTrue(StrandsBoardSpec.TouchFloor >= StrandsBoardSpec.StrokePitch)
        val floor = px(StrandsBoardSpec.TouchFloor.value)
        assertEquals(px(2f), with(density) { strandsStroke(2.dp, floor) }, 1e-4f)
    }

    @Test
    fun `the solved path is 29 and 41 percent of the pitch`() {
        assertEquals(0.29f, StrandsOutlinedSpec.ThemeConnector, 0f)
        assertEquals(0.41f, StrandsOutlinedSpec.SpangramConnector, 0f)
    }

    @Test
    fun `an idle outline is softer than the outline token but stays visible, light and dark`() {
        fun contrast(a: Color, b: Color) = (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)
        for (tokens in listOf(LightBoardTokens, DarkBoardTokens)) {
            val drawn = tokens.strandsFlatOutline.copy(alpha = StrandsOutlinedSpec.IdleOutlineAlpha).compositeOver(tokens.boardSurface)
            val shown = contrast(drawn, tokens.boardSurface)
            assertTrue("softer than the token: $shown", shown < contrast(tokens.strandsFlatOutline, tokens.boardSurface))
            assertTrue("still visible: $shown", shown >= 1.5f)
        }
    }
}
