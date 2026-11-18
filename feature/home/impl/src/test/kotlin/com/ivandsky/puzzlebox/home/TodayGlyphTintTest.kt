package com.ivandsky.puzzlebox.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.gameui.GameBrand
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Solved glyph sits on a filled accent disc, so it needs the accent's own on-colour (white fails on cyan and orange). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TodayGlyphTintTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun contrast(foreground: Color, background: Color): Float {
        val l1 = foreground.luminance() + 0.05f
        val l2 = background.luminance() + 0.05f
        return maxOf(l1, l2) / minOf(l1, l2)
    }

    @Test
    fun `a solved glyph clears 4_5 to 1 on every game's disc in light and dark`() {
        val ratios = mutableMapOf<String, Float>()
        composeRule.setContent {
            listOf(false, true).forEach { dark ->
                PuzzleBoxTheme(darkTheme = dark) {
                    AccentSlot.entries.forEach { slot ->
                        val brand = GameBrand.Neutral.copy(accent = slot)
                        ratios["$slot dark=$dark"] = contrast(glyphTint(brand, TodayStatus.Solved(36_000L)), slot.colors().accent)
                    }
                }
            }
        }
        composeRule.waitForIdle()

        assertTrue(ratios.size == AccentSlot.entries.size * 2)
        ratios.forEach { (case, ratio) -> assertTrue("$case solved glyph = $ratio", ratio >= 4.5f) }
    }
}
