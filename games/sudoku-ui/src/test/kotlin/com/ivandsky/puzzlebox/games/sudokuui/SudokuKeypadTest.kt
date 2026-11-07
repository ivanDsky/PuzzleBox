package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

        assertEquals(SudokuKeypadModel(6, listOf(4, 6, 5, 6, 6, 5), notesMode = false, canClear = true, solved = false), model)
    }

    @Test
    fun `clear is off while only givens are on the board and a solved board is solved`() {
        val givensOnly = sudokuKeypadModel(boardOf(0 to SudokuCell.Given(1)), notesMode = false)
        assertEquals(false, givensOnly.canClear)
        assertEquals(false, givensOnly.solved)

        val solution = listOf(1, 2, 3, 4, 5, 6, 4, 5, 6, 1, 2, 3, 2, 3, 1, 5, 6, 4, 5, 6, 4, 2, 3, 1, 3, 1, 2, 6, 4, 5, 6, 4, 5, 3, 1, 2)
        val solved = SudokuBoard(mini, solution.map { SudokuCell.Entry(it) })
        assertEquals(true, sudokuKeypadModel(solved, notesMode = false).solved)
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
        setKeypad(SudokuKeypadModel(6, listOf(4, 3, 5, 2, 4, 3), notesMode = false, canClear = true, solved = false), onDigit = { digits += it })

        composeRule.onNodeWithContentDescription("4, 2 left").assert(hasRole(Role.Button)).performClick()
        composeRule.onNodeWithContentDescription("1, 4 left").performClick()

        assertEquals(listOf(4, 1), digits)
    }

    @Test
    fun `notes mode announces keys as notes`() {
        setKeypad(SudokuKeypadModel(6, listOf(4, 3, 5, 2, 4, 3), notesMode = true, canClear = true, solved = false))

        composeRule.onNodeWithContentDescription("Note 4, 2 left").assertExists()
        composeRule.onNodeWithContentDescription("4, 2 left").assertDoesNotExist()
    }

    @Test
    fun `a key at zero remaining stays enabled and clickable`() {
        val digits = mutableListOf<Int>()
        setKeypad(SudokuKeypadModel(6, listOf(4, 0, 5, 2, 4, 3), notesMode = false, canClear = true, solved = false), onDigit = { digits += it })

        composeRule.onNodeWithContentDescription("2, 0 left").assertIsEnabled().performClick()

        assertEquals(listOf(2), digits)
    }

    @Test
    fun `erase calls back`() {
        var erased = 0
        setKeypad(SudokuKeypadModel(6, List(6) { 6 }, notesMode = false, canClear = true, solved = false), onErase = { erased++ })

        composeRule.onNodeWithText("Erase").performClick()

        assertEquals(1, erased)
    }

    @Test
    fun `notes, erase and clear share one row under the digits in three equal widths and clear calls back`() {
        var cleared = 0
        setKeypad(SudokuKeypadModel(6, List(6) { 6 }, notesMode = false, canClear = true, solved = false), onClear = { cleared++ })
        val actions = listOf("Notes", "Erase", "Clear").map { composeRule.onNodeWithText(it).getUnclippedBoundsInRoot() }
        val lastDigit = composeRule.onNodeWithContentDescription("6, 6 left").getUnclippedBoundsInRoot()

        assertEquals(1, actions.map { it.top }.distinct().size)
        assertTrue("actions $actions not under the digits $lastDigit", actions.first().top > lastDigit.bottom)
        val widths = actions.map { it.width }
        assertTrue("action widths $widths", widths.max() - widths.min() < 1.dp)
        composeRule.onNodeWithTag("sudoku.clear").assertIsEnabled().performClick()
        assertEquals(1, cleared)
    }

    @Test
    fun `clear is disabled with nothing to clear`() {
        setKeypad(SudokuKeypadModel(6, List(6) { 6 }, notesMode = false, canClear = false, solved = false))

        composeRule.onNodeWithTag("sudoku.clear").assertIsNotEnabled()
        composeRule.onNodeWithText("Erase").assertIsEnabled()
    }

    @Test
    fun `a solved board turns notes, erase and clear off`() {
        setKeypad(SudokuKeypadModel(6, List(6) { 0 }, notesMode = false, canClear = true, solved = true))

        composeRule.onNodeWithText("Notes").assertIsNotEnabled()
        composeRule.onNodeWithText("Erase").assertIsNotEnabled()
        composeRule.onNodeWithTag("sudoku.clear").assertIsNotEnabled()
    }

    @Test
    fun `notes toggle is a switch whose state follows the model`() {
        var notesMode by mutableStateOf(false)
        composeRule.setContent {
            PuzzleBoxTheme {
                SudokuKeypad(
                    model = SudokuKeypadModel(6, List(6) { 6 }, notesMode, canClear = true, solved = false),
                    onDigit = {},
                    onErase = {},
                    onClear = {},
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

    @Test
    fun `the notes column drops the button row for a notes switch key beside the digits`() {
        var notesMode by mutableStateOf(false)
        var solved by mutableStateOf(false)
        composeRule.setContent {
            PuzzleBoxTheme {
                SudokuKeypad(
                    model = SudokuKeypadModel(6, List(6) { 6 }, notesMode, canClear = true, solved = solved),
                    onDigit = {},
                    onErase = {},
                    onClear = {},
                    onToggleNotes = { notesMode = !notesMode },
                    actions = SudokuKeypadActions.NotesColumn,
                )
            }
        }
        composeRule.onNodeWithText("Erase").assertDoesNotExist()
        composeRule.onNodeWithTag("sudoku.clear").assertDoesNotExist()
        val toggle = composeRule.onNodeWithContentDescription("Notes")
        toggle.assert(hasRole(Role.Switch)).assert(hasStateDescription("Off"))
        val three = composeRule.onNodeWithContentDescription("3, 6 left").getUnclippedBoundsInRoot()
        val six = composeRule.onNodeWithContentDescription("6, 6 left").getUnclippedBoundsInRoot()
        val key = toggle.getUnclippedBoundsInRoot()
        assertEquals(three.top, key.top)
        assertEquals(six.bottom, key.bottom)
        assertTrue("toggle $key not beside 3 $three", key.left > three.right)

        toggle.performClick()

        toggle.assert(hasStateDescription("On"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        composeRule.onNodeWithContentDescription("Note 1, 6 left").assertExists()
        solved = true
        toggle.assertIsNotEnabled()
    }

    @Test
    fun `digits sit in three columns - nine in three rows, six in two`() {
        var size by mutableStateOf(9)
        composeRule.setContent {
            PuzzleBoxTheme {
                SudokuKeypad(
                    model = SudokuKeypadModel(size, List(size) { size }, notesMode = false, canClear = true, solved = false),
                    onDigit = {},
                    onErase = {},
                    onClear = {},
                    onToggleNotes = {},
                    modifier = Modifier.width(320.dp),
                )
            }
        }
        for (n in listOf(9, 6)) {
            size = n
            val keys = (1..n).map { composeRule.onNodeWithContentDescription("$it, $n left").getUnclippedBoundsInRoot() }
            val rows = keys.chunked(3)
            assertEquals(n / 3, keys.map { it.top }.distinct().size)
            rows.forEach { row -> assertEquals(1, row.map { it.top }.distinct().size) }
            assertEquals(List(n / 3) { keys[0].left }, rows.map { it.first().left })
            keys.forEach { assertTrue("key $it is under 48dp", it.width >= 48.dp && it.height >= 48.dp) }
        }
    }

    @Test
    fun `a cramped 9x9 keypad puts notes, erase and clear in a fourth column, one per digit row`() {
        setColumnKeypad(size = 9)
        val rowTops = listOf(1, 4, 7).map { composeRule.onNodeWithContentDescription("$it, 9 left").getUnclippedBoundsInRoot().top }
        val three = composeRule.onNodeWithContentDescription("3, 9 left").getUnclippedBoundsInRoot()
        val actions = listOf("Notes", "Erase", "Clear").map { composeRule.onNodeWithText(it).getUnclippedBoundsInRoot() }

        actions.zip(rowTops).forEach { (action, top) -> assertEquals(top.value, action.top.value, 1f) }
        actions.forEach { assertTrue("$it not beside the digits $three", it.left > three.right) }
        actions.forEach { assertEquals(30f, it.height.value, 0.5f) }
    }

    @Test
    fun `a cramped 6x6 keypad puts notes over erase and clear side by side in the fourth column`() {
        setColumnKeypad(size = 6)
        val one = composeRule.onNodeWithContentDescription("1, 6 left").getUnclippedBoundsInRoot()
        val four = composeRule.onNodeWithContentDescription("4, 6 left").getUnclippedBoundsInRoot()
        val notes = composeRule.onNodeWithText("Notes").getUnclippedBoundsInRoot()
        val erase = composeRule.onNodeWithText("Erase").getUnclippedBoundsInRoot()
        val clear = composeRule.onNodeWithTag("sudoku.clear").getUnclippedBoundsInRoot()

        assertEquals(one.top.value, notes.top.value, 1f)
        assertEquals(four.top.value, erase.top.value, 1f)
        assertEquals(erase.top, clear.top)
        assertTrue("erase $erase not left of clear $clear", erase.right < clear.left)
        assertTrue("halves under 48dp: $erase $clear", erase.width >= 47.5.dp && clear.width >= 47.5.dp)
    }

    private fun setColumnKeypad(size: Int) {
        composeRule.setContent {
            PuzzleBoxTheme {
                SudokuKeypad(
                    model = SudokuKeypadModel(size, List(size) { size }, notesMode = false, canClear = true, solved = false),
                    onDigit = {},
                    onErase = {},
                    onClear = {},
                    onToggleNotes = {},
                    modifier = Modifier.width(336.dp),
                    keyHeight = 30.dp,
                    actions = SudokuKeypadActions.Column,
                )
            }
        }
    }

    private fun setKeypad(
        model: SudokuKeypadModel,
        onDigit: (Int) -> Unit = {},
        onErase: () -> Unit = {},
        onClear: () -> Unit = {},
    ) {
        composeRule.setContent {
            PuzzleBoxTheme {
                SudokuKeypad(model = model, onDigit = onDigit, onErase = onErase, onClear = onClear, onToggleNotes = {})
            }
        }
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun hasStateDescription(description: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, description)
}
