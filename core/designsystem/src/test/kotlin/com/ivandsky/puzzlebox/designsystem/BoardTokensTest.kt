package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

// Guards for the board palette (ticket 20, values = research U2's primary table;
// ticket 27 softened dark's trace/on-path blue off the scheme — see BoardTokens).
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
    fun `board highlights pin the Splash game colours`() {
        val light = LightBoardTokens
        val dark = DarkBoardTokens
        assertEquals(Color(0xFF009DDE), light.pathTrace)
        assertEquals(Color(0xFF0074A5), light.pathTraceLip)
        assertEquals(Color(0xFF21B7FF), dark.pathTrace)
        assertEquals(Color(0xFF0077AA), dark.pathTraceLip)
        assertEquals(Color(0xFFCB6400), light.sunFill)
        assertEquals(Color(0xFFA69BFF), dark.boardSelection)
        assertEquals(Color(0xFF00C8BD), light.sudokuSelection)
        assertEquals(Color(0xFF009B92), light.sudokuSelectionLip)
        assertEquals(Color(0xFF003C38), dark.sudokuPeer)
        assertEquals(Color(0xFFFF478A), light.strandsThemeFill)
        assertEquals(Color(0xFFFF63E4), dark.strandsThemeAltFill)
        assertEquals(Color(0xFFFECF00), dark.strandsSpangramFill)
        assertEquals(Color(0xFFE40070), light.strandsSelectionFill)
        assertEquals(Color(0xFFCF0064), light.strandsThemeLip)
        assertEquals(Color(0xFFB39100), dark.strandsSpangramLip)
        assertEquals(Color(0xFFFFDDC9), light.tangoHintFill)
        assertEquals(Color(0xFF0F0300), dark.tangoHintLip)
    }

    @Test
    fun `lips sit darker than their fills in both themes`() {
        for (tokens in listOf(DarkBoardTokens, LightBoardTokens)) {
            assertTrue(tokens.sudokuSelectionLip.luminance() < tokens.sudokuSelection.luminance())
            assertTrue(tokens.pathTraceLip.luminance() < tokens.pathTrace.luminance())
            assertTrue(tokens.tangoHintLip.luminance() < tokens.tangoHintFill.luminance())
            assertTrue(tokens.strandsThemeLip.luminance() < tokens.strandsThemeFill.luminance())
            assertTrue(tokens.strandsThemeAltLip.luminance() < tokens.strandsThemeAltFill.luminance())
            assertTrue(tokens.strandsSpangramLip.luminance() < tokens.strandsSpangramFill.luminance())
            assertTrue(tokens.strandsSelectionLip.luminance() < tokens.strandsSelectionFill.luminance())
        }
    }

    @Test
    fun `labels meet AA on their own dot fills in both themes`() {
        for (tokens in listOf(DarkBoardTokens, LightBoardTokens)) {
            assertTrue(contrast(tokens.waypointLabel, tokens.waypointDot) >= 4.5f)
        }
    }

    // Tango glyph fills (design-direction §3, floor §10.2): silhouette carries the
    // identity, so the fill needs the 3:1 non-text bar vs boardSurface, not 4.5.
    @Test
    fun `tango sun and moon fills clear the non-text bar vs boardSurface in both themes`() {
        for (tokens in listOf(DarkBoardTokens, LightBoardTokens)) {
            assertTrue(contrast(tokens.sunFill, tokens.boardSurface) >= 3f)
            assertTrue(contrast(tokens.moonFill, tokens.boardSurface) >= 3f)
        }
    }

    // Tango mark glyphs (`=`/`×` strokes + caption text) render in onSurfaceVariant —
    // floor §10.3: marks are TEXT-sized glyphs, 4.5:1 vs boardSurface, both modes.
    @Test
    fun `tango mark color clears AA vs boardSurface in both themes`() {
        for (scheme in listOf(LightColorScheme, DarkColorScheme)) {
            val boardSurface = if (scheme === LightColorScheme) scheme.surface else scheme.surfaceContainer
            assertTrue(contrast(scheme.onSurfaceVariant, boardSurface) >= 4.5f)
        }
    }

    // Tango violation border/dash renders in colorScheme.error — floor §10.5:
    // 3:1 non-text vs boardSurface, both modes.
    @Test
    fun `tango error color clears the non-text bar vs boardSurface in both themes`() {
        for (scheme in listOf(LightColorScheme, DarkColorScheme)) {
            val boardSurface = if (scheme === LightColorScheme) scheme.surface else scheme.surfaceContainer
            assertTrue(contrast(scheme.error, boardSurface) >= 3f)
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

    // Strands (spec A32): black letters sit on the tinted tiles, so each fill needs 4.5:1
    // against the tile text; the dashed hint ring is non-text (3:1) and the idle letter is
    // text on the bare board (4.5:1).
    @Test
    fun `strands tile text meets AA on every tinted fill in both themes`() {
        for (tokens in listOf(DarkBoardTokens, LightBoardTokens)) {
            for (fill in listOf(tokens.strandsThemeFill, tokens.strandsSpangramFill, tokens.strandsSelectionFill)) {
                assertTrue(contrast(tokens.strandsTileText, fill) >= 4.5f)
            }
        }
    }

    @Test
    fun `strands hint ring and idle letter clear their bars vs boardSurface in both themes`() {
        for (tokens in listOf(DarkBoardTokens, LightBoardTokens)) {
            assertTrue(contrast(tokens.strandsHintRing, tokens.boardSurface) >= 3f)
            assertTrue(contrast(tokens.strandsIdleLetter, tokens.boardSurface) >= 4.5f)
        }
    }

    // A refused word's path: its ink is text on the red token (4.5:1), the red itself is a
    // non-text connector on the bare board (3:1).
    @Test
    fun `strands refused path ink and connector clear their bars in both themes`() {
        for (tokens in listOf(DarkBoardTokens, LightBoardTokens)) {
            assertTrue(contrast(tokens.strandsPathErrorInk, tokens.strandsPathError) >= 4.5f)
            assertTrue(contrast(tokens.strandsPathError, tokens.boardSurface) >= 3f)
        }
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val fg = foreground.luminance() + 0.05f
        val bg = background.luminance() + 0.05f
        return if (fg > bg) fg / bg else bg / fg
    }
}

