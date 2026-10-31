package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Callback and copy seams of the simple components: buttons commit, chips and the
// two states expose their text to semantics (screen readers included), recovery
// actions fire.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ComponentInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `primary button commits on click`() {
        var clicks = 0
        composeRule.setContent { PuzzleBoxTheme { PrimaryButton("Start puzzle", onClick = { clicks++ }) } }
        composeRule.onNodeWithText("Start puzzle").performClick()
        composeRule.waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun `secondary button commits on click`() {
        var clicks = 0
        composeRule.setContent { PuzzleBoxTheme { SecondaryButton("Cancel", onClick = { clicks++ }) } }
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun `disabled buttons do not commit`() {
        var clicks = 0
        composeRule.setContent { PuzzleBoxTheme { PrimaryButton("Disabled", onClick = { clicks++ }, enabled = false) } }
        composeRule.onNodeWithText("Disabled").performClick()
        composeRule.waitForIdle()
        assertEquals(0, clicks)
    }

    @Test
    fun `chips expose their values to semantics`() {
        composeRule.setContent {
            PuzzleBoxTheme {
                ScoreChip(value = "1:24", label = "Score")
                HintChip(count = 1)
            }
        }
        composeRule.onNodeWithText("Score").assertExists()
        composeRule.onNodeWithText("1:24").assertExists()
        composeRule.onNodeWithText("1 hint").assertExists()
    }

    @Test
    fun `empty state action fires`() {
        var actions = 0
        composeRule.setContent {
            PuzzleBoxTheme {
                EmptyState(
                    title = "No games yet",
                    message = "Finished puzzles will appear here.",
                    actionLabel = "Browse dailies",
                    onAction = { actions++ },
                )
            }
        }
        composeRule.onNodeWithText("Browse dailies").performClick()
        composeRule.waitForIdle()
        assertEquals(1, actions)
    }

    @Test
    fun `error state retry fires`() {
        var retries = 0
        composeRule.setContent {
            PuzzleBoxTheme {
                ErrorState(
                    title = "Couldn't load",
                    message = "Check your connection.",
                    onRetry = { retries++ },
                )
            }
        }
        composeRule.onNodeWithText("Try again").performClick()
        composeRule.waitForIdle()
        assertEquals(1, retries)
    }
}
