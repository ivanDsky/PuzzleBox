package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SudokuKeypadTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val mini = SudokuGeometry(size = 6, boxRows = 2, boxCols = 3)

    private fun boardOf(vararg placed: Pair<Int, SudokuCell>): SudokuBoard {
        val cells = MutableList<SudokuCell>(mini.cellCount) { SudokuCell.Empty }
        placed.forEach { (idx, cell) -> cells[idx] = cell }
        return SudokuBoard(mini, cells)
    }

    @Test
    fun `remaining counts givens and entries but not notes`() {
        val board = boardOf(
            0 to SudokuCell.Given(1),
            1 to SudokuCell.Entry(1),
            2 to SudokuCell.Given(3),
            3 to SudokuCell.Notes(0b11),
            4 to SudokuCell.Entry(6, notes = 0b10),
        )

        val model = sudokuKeypadModel(board, notesMode = false)

        assertEquals(SudokuKeypadModel(6, listOf(4, 6, 5, 6, 6, 5), notesMode = false), model)
    }

    @Test
    fun `a fully placed digit has zero remaining and over-placement never goes negative`() {
        val board = boardOf(*(0 until 7).map { it to SudokuCell.Entry(2) }.toTypedArray())

        val model = sudokuKeypadModel(board, notesMode = true)

        assertEquals(0, model.remaining[1])
        assertEquals(true, model.notesMode)
    }

    @Test
    fun `a 9x9 board gets nine keys`() {
        val classic = SudokuGeometry(size = 9, boxRows = 3, boxCols = 3)
        val board = SudokuBoard(classic, List(classic.cellCount) { SudokuCell.Empty })

        assertEquals(List(9) { 9 }, sudokuKeypadModel(board, notesMode = false).remaining)
    }

    @Test
    fun `digit keys announce digit and remaining count and report their digit`() {
        val digits = mutableListOf<Int>()
        setKeypad(SudokuKeypadModel(6, listOf(4, 3, 5, 2, 4, 3), notesMode = false), onDigit = { digits += it })

        composeRule.onNodeWithContentDescription("4, 2 left").assert(hasRole(Role.Button)).performClick()
        composeRule.onNodeWithContentDescription("1, 4 left").performClick()

        assertEquals(listOf(4, 1), digits)
    }

    @Test
    fun `notes mode announces keys as notes`() {
        setKeypad(SudokuKeypadModel(6, listOf(4, 3, 5, 2, 4, 3), notesMode = true))

        composeRule.onNodeWithContentDescription("Note 4, 2 left").assertExists()
        composeRule.onNodeWithContentDescription("4, 2 left").assertDoesNotExist()
    }

    @Test
    fun `a key at zero remaining stays enabled and clickable`() {
        val digits = mutableListOf<Int>()
        setKeypad(SudokuKeypadModel(6, listOf(4, 0, 5, 2, 4, 3), notesMode = false), onDigit = { digits += it })

        composeRule.onNodeWithContentDescription("2, 0 left").assertIsEnabled().performClick()

        assertEquals(listOf(2), digits)
    }

    @Test
    fun `erase calls back`() {
        var erased = 0
        setKeypad(SudokuKeypadModel(6, List(6) { 6 }, notesMode = false), onErase = { erased++ })

        composeRule.onNodeWithText("Erase").performClick()

        assertEquals(1, erased)
    }

    @Test
    fun `notes toggle is a switch whose state follows the model`() {
        var notesMode by mutableStateOf(false)
        composeRule.setContent {
            PuzzleBoxTheme {
                SudokuKeypad(
                    model = SudokuKeypadModel(6, List(6) { 6 }, notesMode),
                    onDigit = {},
                    onErase = {},
                    onToggleNotes = { notesMode = !notesMode },
                )
            }
        }
        val toggle = composeRule.onNodeWithText("Notes")
        toggle.assert(hasRole(Role.Switch)).assert(hasStateDescription("Off"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))

        toggle.performClick()

        toggle.assert(hasStateDescription("On"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        composeRule.onNodeWithContentDescription("Note 1, 6 left").assertExists()
    }

    private fun setKeypad(
        model: SudokuKeypadModel,
        onDigit: (Int) -> Unit = {},
        onErase: () -> Unit = {},
    ) {
        composeRule.setContent {
            PuzzleBoxTheme {
                SudokuKeypad(model = model, onDigit = onDigit, onErase = onErase, onToggleNotes = {})
            }
        }
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun hasStateDescription(description: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, description)
}
