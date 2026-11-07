package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Spec A1: every accent slot resolves through the RESOLVED theme — a forced light or
// dark PuzzleBoxTheme flips each slot's four roles — and no two slots share a hue.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AccentTokensTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `every slot resolves to different light and dark values under the theme`() {
        val resolved = mutableMapOf<Boolean, Map<AccentSlot, GameAccent>>()
        composeRule.setContent {
            Row {
                for (dark in listOf(false, true)) {
                    PuzzleBoxTheme(darkTheme = dark) {
                        resolved[dark] = AccentSlot.entries.associateWith { it.colors() }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        for (slot in AccentSlot.entries) {
            val light = resolved.getValue(false).getValue(slot)
            val dark = resolved.getValue(true).getValue(slot)
            assertNotEquals("$slot accent", light.accent, dark.accent)
            assertNotEquals("$slot container", light.container, dark.container)
            assertNotEquals("$slot onContainer", light.onContainer, dark.onContainer)
            assertNotEquals("$slot lip", light.lip, dark.lip)
        }
    }

    @Test
    fun `no two slots share an accent in either mode`() {
        for (palette in listOf(LightAccentTokens, DarkAccentTokens)) {
            assertEquals(AccentSlot.entries.toSet(), palette.keys)
            val accents = palette.values.map { it.accent }
            assertEquals(accents.size, accents.toSet().size)
        }
    }

    @Test
    fun `hue slots pin the Splash set`() {
        assertEquals(
            listOf(0xFF00B4FE, 0xFF002A3F, 0xFFCAEAFF, 0xFF006C9B, 0xFF008AC4),
            LightAccentTokens.getValue(AccentSlot.Blue).argb(),
        )
        assertEquals(
            listOf(0xFFFF478A, 0xFF4D0021, 0xFFFFD9E2, 0xFFC4005F, 0xFFCF0064),
            LightAccentTokens.getValue(AccentSlot.Pink).argb(),
        )
        assertEquals(
            listOf(0xFF00E8DC, 0xFF002E2B, 0xFF003C38, 0xFF00E8DC, 0xFF00A198),
            DarkAccentTokens.getValue(AccentSlot.Teal).argb(),
        )
    }

    @Test
    fun `ink holds AA on every fill and every lip sits darker than its fill`() {
        for (palette in listOf(LightAccentTokens, DarkAccentTokens)) {
            for ((slot, accent) in palette) {
                assertTrue("$slot onAccent", contrast(accent.onAccent, accent.accent) >= 4.5f)
                assertTrue("$slot onContainer", contrast(accent.onContainer, accent.container) >= 4.5f)
                assertTrue("$slot lip", accent.lip.luminance() < accent.accent.luminance())
            }
        }
    }

    private fun GameAccent.argb() =
        listOf(accent, onAccent, container, onContainer, lip).map { it.toArgb().toLong() and 0xFFFFFFFFL }

    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }
}
