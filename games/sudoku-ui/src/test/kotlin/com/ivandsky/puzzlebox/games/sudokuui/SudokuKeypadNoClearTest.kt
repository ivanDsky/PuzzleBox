package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Under a play tuning Clear is in the shared control row, so the keypad leaves it out (showClear false).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SudokuKeypadNoClearTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val model = SudokuKeypadModel(6, List(6) { 6 }, notesMode = false, solved = false)

    private fun setKeypad(actions: SudokuKeypadActions, showClear: Boolean) {
        composeRule.setContent {
            PuzzleBoxTheme {
                SudokuKeypad(
                    model = model,
                    onDigit = {},
                    onErase = {},
                    onClear = {},
                    onToggleNotes = {},
                    actions = actions,
                    showClear = showClear,
                )
            }
        }
    }

    private fun clearCount() =
        composeRule.onAllNodesWithText("Clear").fetchSemanticsNodes().size +
            composeRule.onAllNodesWithContentDescription("Clear").fetchSemanticsNodes().size

    @Test
    fun `the row without Clear holds Notes and Erase only`() {
        setKeypad(SudokuKeypadActions.Row, showClear = false)

        composeRule.onNodeWithText("Notes").assertIsDisplayed()
        composeRule.onNodeWithText("Erase").assertIsDisplayed()
        assertEquals(0, clearCount())
    }

    @Test
    fun `the column without Clear stacks Notes over Erase`() {
        setKeypad(SudokuKeypadActions.Column, showClear = false)

        assertEquals(0, clearCount())
        val notes = composeRule.onNodeWithContentDescription("Notes").getUnclippedBoundsInRoot()
        val erase = composeRule.onNodeWithContentDescription("Erase").getUnclippedBoundsInRoot()
        assertTrue("notes $notes over erase $erase", notes.bottom <= erase.top)
        assertEquals("one column", notes.left.value, erase.left.value, 0.5f)
    }

    @Test
    fun `with Clear the keypad is today's`() {
        setKeypad(SudokuKeypadActions.Row, showClear = true)

        assertEquals(1, clearCount())
        assertTrue(composeRule.onNodeWithText("Erase").getUnclippedBoundsInRoot().width >= 48.dp)
    }

    private val androidx.compose.ui.unit.DpRect.width get() = right - left
}
