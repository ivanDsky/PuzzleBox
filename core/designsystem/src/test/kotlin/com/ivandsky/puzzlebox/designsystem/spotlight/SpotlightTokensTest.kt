package com.ivandsky.puzzlebox.designsystem.spotlight

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.ivandsky.puzzlebox.designsystem.BoardColors
import com.ivandsky.puzzlebox.designsystem.DarkBoardTokens
import com.ivandsky.puzzlebox.designsystem.DarkColorScheme
import com.ivandsky.puzzlebox.designsystem.LightBoardTokens
import com.ivandsky.puzzlebox.designsystem.LightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Guards for the Spotlight's role distinguishability and total motion time (A13, A16). Contrast is
// luminance-only, so the >=3:1 checks double as greyscale checks: no role depends on hue alone. The
// excluded x and the target ghost are user-approved alphas, not held to a glyph-vs-surface ratio; their
// measured ratios are only printed.
class SpotlightTokensTest {

    private val themes: List<Triple<String, BoardColors, ColorScheme>> = listOf(
        Triple("dark", DarkBoardTokens, DarkColorScheme),
        Triple("light", LightBoardTokens, LightColorScheme),
    )

    @Test
    fun `lit ink differs from the same ink under the dim overlay`() {
        for ((name, board, scheme) in themes) {
            val lit = SpotlightTokens.hairline(scheme)
            val dimmed = SpotlightTokens.dimOverlay(board.boardSurface).compositeOver(lit)
            val ratio = contrast(lit, dimmed)
            println("$name lit vs dimmed ink: $ratio")
            assertTrue("$name lit vs dimmed $ratio", ratio > 1f)
        }
    }

    @Test
    fun `culprit badge bar reads against its error disc by shape, not hue`() {
        for ((name, _, scheme) in themes) {
            val ratio = contrast(SpotlightTokens.culpritBadgeBar(scheme), SpotlightTokens.culprit(scheme))
            assertTrue("$name badge bar vs error $ratio", ratio >= 3f)
        }
    }

    @Test
    fun `culprit and hairline clear 3 to 1 against the board surface in luminance`() {
        for ((name, board, scheme) in themes) {
            val culprit = contrast(SpotlightTokens.culprit(scheme), board.boardSurface)
            val hairline = contrast(SpotlightTokens.hairline(scheme), board.boardSurface)
            assertTrue("$name culprit vs surface $culprit", culprit >= 3f)
            assertTrue("$name hairline vs surface $hairline", hairline >= 3f)
        }
    }

    @Test
    fun `callout text is readable and the bubble stands off the board`() {
        for ((name, board, scheme) in themes) {
            val text = contrast(SpotlightTokens.calloutText(scheme), SpotlightTokens.calloutBubble(scheme))
            val bubble = contrast(SpotlightTokens.calloutBubble(scheme), board.boardSurface)
            assertTrue("$name callout text $text", text >= 4.5f)
            assertTrue("$name bubble vs surface $bubble", bubble >= 3f)
        }
    }

    @Test
    fun `ghost and excluded ratios are printed for information only`() {
        for ((name, board, scheme) in themes) {
            val ghost = SpotlightTokens.targetGhost(scheme).compositeOver(board.boardSurface)
            val cross = SpotlightTokens.excluded(scheme).compositeOver(board.boardSurface)
            println("$name target ghost vs surface: ${contrast(ghost, board.boardSurface)}")
            println("$name excluded x vs surface: ${contrast(cross, board.boardSurface)}")
        }
    }

    @Test
    fun `approved alphas are pinned`() {
        assertEquals(0.40f, SpotlightTokens.DIM_ALPHA, 0f)
        assertEquals(0.28f, SpotlightTokens.TARGET_GHOST_ALPHA, 0f)
        assertEquals(0.50f, SpotlightTokens.EXCLUDED_ALPHA, 0f)
    }

    @Test
    fun `the whole sequence ends within 400 ms and no part ends later than the pulse`() {
        val end = SpotlightTokens.PULSE_DELAY_MS + SpotlightTokens.PULSE_DURATION_MS
        assertTrue(end <= SpotlightTokens.TOTAL_MS)
        assertTrue(SpotlightTokens.FADE_MS <= end)
        val staggerEnd = SpotlightTokens.EXCLUDED_MAX_STAGGERED * SpotlightTokens.EXCLUDED_STAGGER_MS
        assertTrue(staggerEnd <= end)
        assertEquals(1.12f, SpotlightTokens.PULSE_PEAK_SCALE, 0f)
        assertTrue(SpotlightTokens.EXCLUDED_START_SCALE < SpotlightTokens.CALLOUT_START_SCALE)
        assertTrue(SpotlightTokens.CALLOUT_START_SCALE < 1f)
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val fg = foreground.luminance() + 0.05f
        val bg = background.luminance() + 0.05f
        return if (fg > bg) fg / bg else bg / fg
    }
}
