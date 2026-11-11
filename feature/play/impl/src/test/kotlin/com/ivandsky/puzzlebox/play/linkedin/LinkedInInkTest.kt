package com.ivandsky.puzzlebox.play.linkedin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameCardColors
import com.ivandsky.puzzlebox.play.libraryCardColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The LinkedIn lines' ink reads at 4.5:1 on the card every game's accent gives, in light and dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LinkedInInkTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun inkReadsAtFourPointFiveOnEveryAccentInBothThemes() {
        val cards = mutableMapOf<Pair<Boolean, AccentSlot>, GameCardColors>()
        composeRule.setContent {
            for (dark in listOf(false, true)) {
                PuzzleBoxTheme(darkTheme = dark) {
                    for (accent in AccentSlot.entries) cards[dark to accent] = libraryCardColors(GameBrand(accent, GameGlyphs.Zip))
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(2 * AccentSlot.entries.size, cards.size)
        for ((key, colors) in cards) {
            val (dark, accent) = key
            val ratio = wcag(linkedInInk(colors), colors.fill)
            assertTrue("${if (dark) "dark" else "light"} $accent: ${linkedInInk(colors)} on ${colors.fill} is $ratio:1", ratio >= 4.5f)
        }
    }

    private fun wcag(a: Color, b: Color): Float {
        val hi = maxOf(a.luminance(), b.luminance()) + 0.05f
        val lo = minOf(a.luminance(), b.luminance()) + 0.05f
        return hi / lo
    }
}
