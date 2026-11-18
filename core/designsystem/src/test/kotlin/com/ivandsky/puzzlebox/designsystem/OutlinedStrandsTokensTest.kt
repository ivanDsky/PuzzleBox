package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The outlined Strands roles (nav-rethink 131, prototype 07 C): their values, the floors they clear in both
 * themes, and that the roles the readable look reads keep every value they had.
 */
class OutlinedStrandsTokensTest {

    @Test
    fun `the outlined roles hold the prototype values in both themes`() {
        assertEquals(Color(0xFFFFA6C3), LightBoardTokens.strandsThemeTint)
        assertEquals(Color(0xFF8A3558), DarkBoardTokens.strandsThemeTint)
        assertEquals(Color(0xFFE5A6FF), LightBoardTokens.strandsThemeAltTint)
        assertEquals(Color(0xFF7C3487), DarkBoardTokens.strandsThemeAltTint)
        assertEquals(Color(0xFFE40070), LightBoardTokens.strandsFlatSelection)
        assertEquals(Color(0xFFFF77A0), DarkBoardTokens.strandsFlatSelection)
        assertEquals(Color(0xFFF23F82), LightBoardTokens.strandsFlatHintRing)
        assertEquals(Color(0xFFFF77A0), DarkBoardTokens.strandsFlatHintRing)
        assertEquals(LightColorScheme.outline, LightBoardTokens.strandsFlatOutline)
        assertEquals(DarkColorScheme.outline, DarkBoardTokens.strandsFlatOutline)
    }

    @Test
    fun `board ink is text on both tints in both themes`() {
        for (tokens in listOf(DarkBoardTokens, LightBoardTokens)) {
            assertTrue(contrast(tokens.boardInk, tokens.strandsThemeTint) >= 4.5f)
            assertTrue(contrast(tokens.boardInk, tokens.strandsThemeAltTint) >= 4.5f)
        }
    }

    @Test
    fun `the outline, the selection outline and the hint ring clear 3 to 1 on the board in both themes`() {
        for (tokens in listOf(DarkBoardTokens, LightBoardTokens)) {
            assertTrue(contrast(tokens.strandsFlatOutline, tokens.boardSurface) >= 3f)
            assertTrue(contrast(tokens.strandsFlatSelection, tokens.boardSurface) >= 3f)
            assertTrue(contrast(tokens.strandsFlatHintRing, tokens.boardSurface) >= 3f)
        }
    }

    @Test
    fun `the roles the readable look reads are not touched`() {
        assertEquals(Color(0xFFFF528F), LightBoardTokens.strandsHintRing)
        assertEquals(Color(0xFFFF528F), LightBoardTokens.strandsHintConnector)
        assertEquals(Color(0xFFFF478A), LightBoardTokens.strandsThemeConnector)
        assertEquals(Color(0xFFE40070), DarkBoardTokens.strandsSelectionFill)
        assertEquals(Color(0xFFFF77A0), DarkBoardTokens.strandsHintRing)
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val fg = foreground.luminance() + 0.05f
        val bg = background.luminance() + 0.05f
        return if (fg > bg) fg / bg else bg / fg
    }
}
