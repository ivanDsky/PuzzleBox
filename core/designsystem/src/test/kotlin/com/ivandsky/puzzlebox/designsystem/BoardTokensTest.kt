package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Guards for the board palette (ticket 20, values = research U2's primary table).
// Bars per U2 / WCAG: labels & dot fills >= 4.5:1 vs boardSurface and label on its
// own fill (1.4.3); trace/wall/selection >= 3:1 (1.4.11 non-text); gridline >= 2:1
// self-imposed visibility floor. These are the bar U2's successors must clear at
// T25 — not a pin on today's numbers.
class BoardTokensTest {

    @Test
    fun `dark and light board tokens are distinct`() {
        val dark = DarkBoardTokens
        val light = LightBoardTokens
        assertNotEquals(dark.boardSurface, light.boardSurface)
        assertNotEquals(dark.waypointDot, light.waypointDot)
        assertNotEquals(dark.boardSelection, light.boardSelection)
    }

    @Test
    fun `labels meet AA on their own dot fills in both themes`() {
        for (tokens in listOf(DarkBoardTokens, LightBoardTokens)) {
            assertTrue(contrast(tokens.waypointLabel, tokens.waypointDot) >= 4.5f)
            assertTrue(contrast(tokens.onPathLabel, tokens.waypointDotOnPath) >= 4.5f)
        }
    }

    @Test
    fun `dot fills meet AA on boardSurface in both themes`() {
        for (tokens in listOf(DarkBoardTokens, LightBoardTokens)) {
            assertTrue(contrast(tokens.waypointDot, tokens.boardSurface) >= 4.5f)
            assertTrue(contrast(tokens.waypointDotOnPath, tokens.boardSurface) >= 4.5f)
        }
    }

    @Test
    fun `non-text roles meet their bars on boardSurface in both themes`() {
        for (tokens in listOf(DarkBoardTokens, LightBoardTokens)) {
            assertTrue(contrast(tokens.pathTrace, tokens.boardSurface) >= 3f)
            assertTrue(contrast(tokens.boardWall, tokens.boardSurface) >= 3f)
            assertTrue(contrast(tokens.boardSelection, tokens.boardSurface) >= 3f)
            assertTrue(contrast(tokens.boardGridLine, tokens.boardSurface) >= 2f)
        }
    }

    @Test
    fun `trace carries the on-path dot hue, distinct from the unvisited dot`() {
        // U2: on-path dots share the trace's blue family by design (continuity of
        // the passed path, trace renders under dots). Dark uses ONE shared blue;
        // light needs two steps (dot #0F78B4 must hit 4.5, trace #1899D6 clears 3).
        assertEquals(DarkBoardTokens.pathTrace, DarkBoardTokens.waypointDotOnPath)
        assertNotEquals(LightBoardTokens.pathTrace, LightBoardTokens.waypointDotOnPath)
        for (tokens in listOf(DarkBoardTokens, LightBoardTokens)) {
            assertNotEquals(tokens.pathTrace, tokens.waypointDot)
        }
    }

    @Test
    fun `region is a surface tint, not a text-bearing fill`() {
        // U2 notes both region values sit within ~1.15:1 of boardSurface — a fill
        // tint only; its boundary gets a wall/gridline stroke when built.
        for (tokens in listOf(DarkBoardTokens, LightBoardTokens)) {
            assertNotEquals(tokens.boardRegion, tokens.boardSurface)
            assertTrue(contrast(tokens.boardRegion, tokens.boardSurface) < 2f)
        }
    }

    @Test
    fun `wall stroke is clearly heavier than gridline and neither is a hairline`() {
        // The original complaint: 1dp hairlines invisible on the dark palette.
        assertTrue(BoardTokens.boardGridLineWeight > 1.dp)
        assertTrue(BoardTokens.boardWallWeight >= BoardTokens.boardGridLineWeight * 2)
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val fg = foreground.luminance() + 0.05f
        val bg = background.luminance() + 0.05f
        return if (fg > bg) fg / bg else bg / fg
    }
}
