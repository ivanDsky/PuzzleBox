package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
            assertNotEquals("$slot onAccent", light.onAccent, dark.onAccent)
            assertNotEquals("$slot container", light.container, dark.container)
            assertNotEquals("$slot onContainer", light.onContainer, dark.onContainer)
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
}
