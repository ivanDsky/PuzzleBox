package com.ivandsky.puzzlebox.gameui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.LocalOnFill
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.colors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameCardColorsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val brand = GameBrand.Neutral.copy(accent = AccentSlot.Teal)

    private data class Resolved(
        val colors: GameCardColors,
        val accent: Color,
        val onFill: Color,
        val container: Color,
        val onVariant: Color,
    )

    /** Resolves every (offline, darkTheme) pair in one composition: the rule sets content once. */
    private fun resolve(vararg cases: Pair<Boolean, Boolean>): List<Resolved> {
        val resolved = arrayOfNulls<Resolved>(cases.size)
        composeRule.setContent {
            cases.forEachIndexed { i, (offline, darkTheme) ->
                PuzzleBoxTheme(darkTheme = darkTheme) {
                    resolved[i] = Resolved(
                        colors = gameCardColors(brand, offline),
                        accent = brand.accent.colors().accent,
                        onFill = LocalOnFill.current,
                        container = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onVariant = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        return resolved.map { requireNotNull(it) }
    }

    @Test
    fun `online card fills with the accent and draws in on-fill`() {
        resolve(false to false, false to true).forEach { r ->
            assertEquals(r.accent, r.colors.fill)
            assertEquals(r.onFill, r.colors.fg)
            assertEquals(r.onFill, r.colors.motif)
            assertEquals(r.onFill.copy(alpha = r.colors.fg2.alpha), r.colors.fg2)
            assertTrue(r.colors.fg2.alpha < 1f)
        }
    }

    @Test
    fun `offline card is neutral with a fainter motif`() {
        val (online, r) = resolve(false to false, true to false)
        assertEquals(r.container, r.colors.fill)
        assertEquals(listOf(r.onVariant, r.onVariant, r.onVariant), listOf(r.colors.fg, r.colors.fg2, r.colors.motif))
        assertTrue(r.colors.motifAlpha < online.colors.motifAlpha)
    }
}
