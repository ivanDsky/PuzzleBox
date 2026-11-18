package com.ivandsky.puzzlebox.play

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.gameui.GameBrand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A library row's text sits on its brand accent, so it takes the accent's own on-colour, not the theme's white. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryCardColorsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class Resolved(val slot: AccentSlot, val dark: Boolean, val fill: Color, val fg: Color, val onAccent: Color)

    private fun resolveAll(): List<Resolved> {
        val resolved = mutableListOf<Resolved>()
        composeRule.setContent {
            for (dark in listOf(false, true)) {
                PuzzleBoxTheme(darkTheme = dark) {
                    for (slot in AccentSlot.entries) {
                        val colors = libraryCardColors(GameBrand.Neutral.copy(accent = slot))
                        resolved += Resolved(slot, dark, colors.fill, colors.fg, slot.colors().onAccent)
                    }
                }
            }
        }
        composeRule.waitForIdle()
        return resolved
    }

    @Test
    fun `every library card draws in its accent's on-colour and clears 4_5 to 1 on the fill`() {
        resolveAll().forEach { r ->
            assertEquals("${r.slot} dark=${r.dark}", r.onAccent, r.fg)
            val ratio = contrast(r.fg, r.fill)
            assertTrue("${r.slot} dark=${r.dark} text = $ratio", ratio >= 4.5f)
        }
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val l1 = foreground.luminance() + 0.05f
        val l2 = background.luminance() + 0.05f
        return maxOf(l1, l2) / minOf(l1, l2)
    }
}
