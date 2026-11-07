package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The chunky in-game button (spec A12, A14): a press reaches the game, a disabled one
// never does, the compact size keeps its label for TalkBack, the toggle is a Switch.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GameButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a game button fires on press and holds its size floor`() {
        var clicks = 0
        composeRule.setContent {
            PuzzleBoxTheme {
                Column {
                    GameButton(onClick = { clicks++ }, icon = PuzzleIcons.Undo, label = "Undo")
                    GameButton(onClick = {}, icon = PuzzleIcons.Hint, label = "Hint", accent = AccentSlot.Teal, size = GameButtonSize.Tall)
                }
            }
        }
        composeRule.onNodeWithText("Undo").assertIsEnabled().assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithText("Hint").assertHeightIsAtLeast(64.dp)
        composeRule.waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun `a disabled game button does not fire`() {
        var clicks = 0
        composeRule.setContent {
            PuzzleBoxTheme {
                GameButton(onClick = { clicks++ }, icon = PuzzleIcons.Redo, label = "Redo", enabled = false)
            }
        }
        composeRule.onNodeWithText("Redo").assertIsNotEnabled().performClick()
        composeRule.waitForIdle()
        assertEquals(0, clicks)
    }

    @Test
    fun `a compact game button is icon only and announces its label`() {
        var clicks = 0
        composeRule.setContent {
            PuzzleBoxTheme {
                GameButton(onClick = { clicks++ }, icon = PuzzleIcons.Erase, label = "Erase", size = GameButtonSize.Compact)
            }
        }
        composeRule.onNode(hasText("Erase")).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Erase").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        composeRule.waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun `a game toggle button is a switch whose state follows checked`() {
        var checked by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        composeRule.setContent {
            PuzzleBoxTheme {
                GameToggleButton(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    icon = PuzzleIcons.Notes,
                    label = "Notes",
                    accent = AccentSlot.Teal,
                    enabled = enabled,
                )
            }
        }
        val toggle = composeRule.onNodeWithText("Notes")
        toggle.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(hasStateDescription("Off"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))

        toggle.performClick()

        toggle.assert(hasStateDescription("On"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        enabled = false
        toggle.assertIsNotEnabled().performClick()
        composeRule.waitForIdle()
        assertEquals(true, checked)
    }
}
