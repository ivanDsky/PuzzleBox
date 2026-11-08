package com.ivandsky.puzzlebox.games.sudokuui.editor

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuDraft
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuEditor
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuSymmetry
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
class SudokuEditorSymmetryTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val editor = SudokuEditor(SudokuGames.MINI)
    private val controller = SudokuEditorController(EditorDraft.fresh(editor, editor.variants.first())) {}

    private fun show() {
        composeRule.setContent {
            PuzzleBoxTheme { SudokuEditorBoard(controller, SudokuGames.MINI.variant.geometry) }
        }
    }

    private fun givens(): List<Int> = (controller.draft.value.state.draft as SudokuDraft).givens

    @Test
    fun symmetry_defaults_to_off_and_cycles_off_rotational_mirror() {
        assertEquals(SudokuSymmetry.Off, controller.symmetry.value)
        controller.cycleSymmetry()
        assertEquals(SudokuSymmetry.Rotational, controller.symmetry.value)
        controller.cycleSymmetry()
        assertEquals(SudokuSymmetry.Mirror, controller.symmetry.value)
        controller.cycleSymmetry()
        assertEquals(SudokuSymmetry.Off, controller.symmetry.value)
    }

    @Test
    fun a_digit_hops_the_selection_to_an_empty_mirror() {
        controller.cycleSymmetry()
        controller.tap(0)
        controller.digit(3)
        assertEquals(35, controller.selected.value)
    }

    @Test
    fun mirror_symmetry_hops_across_the_row() {
        controller.cycleSymmetry()
        controller.cycleSymmetry()
        controller.tap(0)
        controller.digit(3)
        assertEquals(5, controller.selected.value)
    }

    @Test
    fun off_keeps_the_selection_where_it_is() {
        controller.tap(0)
        controller.digit(3)
        assertEquals(0, controller.selected.value)
    }

    @Test
    fun no_hop_when_the_mirror_already_holds_a_digit() {
        controller.tap(35)
        controller.digit(2)
        controller.cycleSymmetry()
        controller.tap(0)
        controller.digit(3)
        assertEquals(0, controller.selected.value)
        assertEquals(3, givens()[0])
    }

    @Test
    fun erasing_does_not_hop() {
        controller.tap(0)
        controller.digit(3)
        controller.cycleSymmetry()
        controller.erase()
        assertEquals(0, controller.selected.value)
        assertEquals(0, givens()[0])
    }

    @Test
    fun the_symmetry_button_cycles_the_controller() {
        show()
        composeRule.onNodeWithTag("sudoku.editor.symmetry").performClick()
        assertEquals(SudokuSymmetry.Rotational, controller.symmetry.value)
    }

    @Test
    fun the_unpaired_badge_counts_empty_cells_whose_mirror_holds_a_given() {
        controller.tap(0)
        controller.digit(3)
        controller.cycleSymmetry()
        show()
        composeRule.onNodeWithTag("sudoku.editor.unpaired").assertTextEquals("1")
        composeRule.onAllNodesWithTag("sudoku.editor.unpaired.cell").assertCountEquals(1)
    }

    @Test
    fun no_unpaired_tags_while_off() {
        controller.tap(0)
        controller.digit(3)
        show()
        composeRule.onAllNodesWithTag("sudoku.editor.unpaired").assertCountEquals(0)
    }

    @Test
    fun hatch_marks_every_empty_peer_of_a_selected_digit() {
        controller.tap(0)
        controller.digit(3)
        show()
        val geometry = SudokuGames.MINI.variant.geometry
        val empty = geometry.peersOf(0).count { givens()[it] == 0 }
        composeRule.onAllNodesWithTag("sudoku.editor.hatch").assertCountEquals(empty)
    }

    @Test
    fun no_hatch_while_the_selected_cell_is_empty_or_nothing_is_selected() {
        controller.tap(0)
        controller.digit(3)
        controller.erase()
        show()
        composeRule.onAllNodesWithTag("sudoku.editor.hatch").assertCountEquals(0)
        controller.tap(0)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("sudoku.editor.hatch").assertCountEquals(0)
    }
}
