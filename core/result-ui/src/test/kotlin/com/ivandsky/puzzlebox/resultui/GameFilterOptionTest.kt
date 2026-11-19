package com.ivandsky.puzzlebox.resultui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.FilterOption
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.gameui.GameBrand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The rows a game or a level becomes in a filter sheet (epic picker-filters, ticket 02): their glyph, mark, tag and the glyph's contrast. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GameFilterOptionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun brand(slot: AccentSlot) = GameBrand.Neutral.copy(accent = slot)

    @Composable
    private fun IconHost(option: FilterOption<String>, tint: Color) {
        Box(Modifier.testTag("host")) { option.icon!!(tint) }
    }

    @Test
    fun `a game row carries its value, label and tag, and a glyph at 24dp`() {
        val option = gameFilterOption("zip", "Zip", brand(AccentSlot.Blue), tag = "x.filter.game.zip")
        composeRule.setContent { PuzzleBoxTheme { IconHost(option, Color.Magenta) } }

        assertEquals(Triple("zip", "Zip", "x.filter.game.zip"), Triple(option.value, option.label, option.tag))
        composeRule.onNodeWithTag("host").assertWidthIsEqualTo(24.dp).assertHeightIsEqualTo(24.dp)
    }

    @Test
    fun `a level row draws the level's own mark at 24dp in the row's tint`() {
        val option = levelFilterOption("hard", "Hard", Difficulty.Level.Hard)
        composeRule.setContent { PuzzleBoxTheme { IconHost(option, Color.Magenta) } }

        assertNull(option.tag)
        composeRule.onNodeWithTag("difficulty.hard").assertIsDisplayed().assertWidthIsEqualTo(24.dp).assertHeightIsEqualTo(24.dp)
    }

    @Test
    fun `every game's glyph tint holds 3 to 1 on the plain row and on the selected row, light and dark`() {
        val failures = mutableListOf<String>()
        composeRule.setContent {
            listOf(false, true).forEach { dark ->
                PuzzleBoxTheme(darkTheme = dark) {
                    val scheme: ColorScheme = MaterialTheme.colorScheme
                    AccentSlot.entries.forEach { slot ->
                        val ink = slot.colors().onContainer
                        mapOf("plain" to scheme.surfaceContainerLow, "selected" to scheme.secondaryContainer).forEach { (row, fill) ->
                            val ratio = contrast(ink, fill)
                            if (ratio < 3f) failures += "$slot on the $row row, dark=$dark: $ratio"
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val top = foreground.luminance() + 0.05f
        val bottom = background.luminance() + 0.05f
        return if (top > bottom) top / bottom else bottom / top
    }
}
