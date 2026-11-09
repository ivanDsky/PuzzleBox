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
import org.junit.Assert.assertTrue
import org.junit.Test

// Guards for the Spotlight's role distinguishability and total motion time (A13, A16). Contrast is
// luminance-only, so the >=3:1 checks double as greyscale checks: no role depends on hue alone. The
// excluded x and the target ghost are user-approved alphas, not held to a glyph-vs-surface ratio.
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
            assertTrue("$name lit vs dimmed $ratio", ratio >= 1.5f)
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
    fun `culprit ink is told apart from the dimmed board by luminance`() {
        // No violation-cue token exists in BoardColors (stripes use the scheme error as is), so the
        // culprit is only checked against the dimmed board ink here.
        for ((name, board, scheme) in themes) {
            val dimmedInk = SpotlightTokens.dimOverlay(board.boardSurface).compositeOver(scheme.onSurface)
            val ratio = contrast(SpotlightTokens.culprit(scheme), dimmedInk)
            println("$name culprit vs dimmed ink: $ratio")
            assertTrue("$name culprit vs dimmed ink $ratio", ratio >= 1.1f)
        }
    }

    @Test
    fun `excluded stays stronger than the ghost and the ghost shows on the dimmed surface`() {
        assertTrue(SpotlightTokens.EXCLUDED_ALPHA > SpotlightTokens.TARGET_GHOST_ALPHA)
        for ((name, board, scheme) in themes) {
            val dimmedSurface = SpotlightTokens.dimOverlay(board.boardSurface).compositeOver(board.boardSurface)
            val ghost = SpotlightTokens.targetGhost(scheme).compositeOver(dimmedSurface)
            val ratio = contrast(ghost, dimmedSurface)
            assertTrue("$name ghost vs dimmed surface $ratio", ratio >= 1.1f)
        }
    }

    @Test
    fun `the worst-case sequence ends within 400 ms`() {
        val lastStaggerStart = (SpotlightTokens.EXCLUDED_MAX_STAGGERED - 1) * SpotlightTokens.EXCLUDED_STAGGER_MS
        val worst = lastStaggerStart + SpotlightTokens.FADE_MS + SpotlightTokens.PULSE_DURATION_MS
        assertTrue("worst $worst", worst <= SpotlightTokens.TOTAL_MS)
        assertTrue(SpotlightTokens.PULSE_DELAY_MS + SpotlightTokens.PULSE_DURATION_MS <= SpotlightTokens.TOTAL_MS)
        assertTrue(SpotlightTokens.EXCLUDED_START_SCALE < SpotlightTokens.CALLOUT_START_SCALE)
        assertTrue(SpotlightTokens.CALLOUT_START_SCALE < 1f)
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val fg = foreground.luminance() + 0.05f
        val bg = background.luminance() + 0.05f
        return if (fg > bg) fg / bg else bg / fg
    }
}
