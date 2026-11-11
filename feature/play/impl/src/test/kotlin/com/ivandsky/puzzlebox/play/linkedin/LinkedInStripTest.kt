package com.ivandsky.puzzlebox.play.linkedin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.LocalLinkedInStrip
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.play.libraryCardColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The card's own content colour reads at 4.5:1 on the strip the LinkedIn lines sit on, for every accent, light and dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LinkedInStripTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class Seen(val fg: Color, val fill: Color, val strip: Color)

    @Test
    fun contentReadsAtFourPointFiveOnTheStripOverEveryAccentInBothThemes() {
        val seen = mutableMapOf<Pair<Boolean, AccentSlot>, Seen>()
        composeRule.setContent {
            for (dark in listOf(false, true)) {
                PuzzleBoxTheme(darkTheme = dark) {
                    val strip = LocalLinkedInStrip.current
                    for (accent in AccentSlot.entries) {
                        val colors = libraryCardColors(GameBrand(accent, GameGlyphs.Zip))
                        seen[dark to accent] = Seen(colors.fg, colors.fill, strip)
                    }
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(2 * AccentSlot.entries.size, seen.size)
        for ((key, card) in seen) {
            val (dark, accent) = key
            val behind = card.strip.compositeOver(card.fill)
            val ratio = wcag(card.fg, behind)
            assertTrue("${if (dark) "dark" else "light"} $accent: ${card.fg} on $behind is $ratio:1", ratio >= 4.5f)
        }
    }

    private fun wcag(a: Color, b: Color): Float {
        val hi = maxOf(a.luminance(), b.luminance()) + 0.05f
        val lo = minOf(a.luminance(), b.luminance()) + 0.05f
        return hi / lo
    }
}
