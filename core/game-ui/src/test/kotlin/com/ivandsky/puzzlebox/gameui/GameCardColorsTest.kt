package com.ivandsky.puzzlebox.gameui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.ivandsky.puzzlebox.designsystem.AccentSlot
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

    private fun brandOf(slot: AccentSlot = AccentSlot.Teal) = GameBrand.Neutral.copy(accent = slot)

    private data class Resolved(
        val colors: GameCardColors,
        val accent: Color,
        val onAccent: Color,
        val container: Color,
        val onVariant: Color,
    )

    /** Resolves every (slot, offline, darkTheme) case in one composition: the rule sets content once. */
    private fun resolve(vararg cases: Triple<AccentSlot, Boolean, Boolean>): List<Resolved> {
        val resolved = arrayOfNulls<Resolved>(cases.size)
        composeRule.setContent {
            cases.forEachIndexed { i, (slot, offline, darkTheme) ->
                val brand = brandOf(slot)
                PuzzleBoxTheme(darkTheme = darkTheme) {
                    resolved[i] = Resolved(
                        colors = gameCardColors(brand, offline),
                        accent = brand.accent.colors().accent,
                        onAccent = brand.accent.colors().onAccent,
                        container = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onVariant = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        return resolved.map { requireNotNull(it) }
    }

    private fun resolve(vararg cases: Pair<Boolean, Boolean>): List<Resolved> =
        resolve(*cases.map { (offline, dark) -> Triple(AccentSlot.Teal, offline, dark) }.toTypedArray())

    @Test
    fun `online card fills with the accent and draws in the accent's own on-colour`() {
        resolve(false to false, false to true).forEach { r ->
            assertEquals(r.accent, r.colors.fill)
            assertEquals(r.onAccent, r.colors.fg)
            assertEquals(r.onAccent, r.colors.motif)
            assertEquals(r.onAccent.copy(alpha = r.colors.fg2.alpha), r.colors.fg2)
            assertTrue(r.colors.fg2.alpha < 1f)
        }
    }

    @Test
    fun `every game's card text clears 4_5 to 1 on its fill in light and dark`() {
        val cases = AccentSlot.entries.flatMap { listOf(Triple(it, false, false), Triple(it, false, true)) }
        resolve(*cases.toTypedArray()).forEachIndexed { i, r ->
            val ratio = contrast(r.colors.fg, r.colors.fill)
            assertTrue("${cases[i].first} dark=${cases[i].third} card text = $ratio", ratio >= 4.5f)
        }
    }

    @Test
    fun `offline card is neutral and flat with a fainter motif`() {
        val (online, r) = resolve(false to false, true to false)
        assertEquals(r.container, r.colors.fill)
        assertEquals(listOf(r.onVariant, r.onVariant, r.onVariant), listOf(r.colors.fg, r.colors.fg2, r.colors.motif))
        assertTrue(r.colors.motifAlpha < online.colors.motifAlpha)
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val l1 = foreground.luminance() + 0.05f
        val l2 = background.luminance() + 0.05f
        return maxOf(l1, l2) / minOf(l1, l2)
    }
}
