package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsBoardSemantics
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Selecting a path keeps its word look and fades the rest of the board (user F9c): the selected
 * path's cells read exactly as when it is not selected, every other cell reads dimmed, and with
 * nothing selected nothing is dimmed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h900dp")
class StrandsEditorSelectionDimTest {

    @get:Rule
    val rule = createComposeRule()

    private val controller = StrandsEditorController(EditorDraft.fresh(StrandsEditor, StrandsDefinition.VARIANT)) {}

    private fun path(vararg cells: Int) {
        cells.forEach(controller::traceTo)
        controller.endTrace()
    }

    private fun show() = rule.setContent {
        PuzzleBoxTheme { StrandsEditorUi.Board(controller, Verdict.Checking, PlayLayout.SinglePane, Modifier) }
    }

    /** The cell's announced state: its tile's description, which carries the cell's look word. */
    private fun look(cell: Int): String? = rule
        .onNodeWithTag("strands.cell.${cell / StrandsDraft.COLS}.${cell % StrandsDraft.COLS}", useUnmergedTree = true)
        .fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.single()

    /** Whether the cell's tile carries the dimmed mark: the board draws it faded. */
    private fun dimmed(cell: Int): Boolean = rule
        .onNodeWithTag("strands.cell.${cell / StrandsDraft.COLS}.${cell % StrandsDraft.COLS}", useUnmergedTree = true)
        .fetchSemanticsNode().config.contains(StrandsBoardSemantics.Dimmed)

    @Test
    fun `the selected path reads as it does unselected`() {
        path(0, 1, 2)
        show()
        val before = listOf(0, 1, 2).map(::look)
        controller.selectPath(0)
        rule.waitForIdle()
        assertEquals(before, listOf(0, 1, 2).map(::look))
    }

    @Test
    fun `every cell outside the selected path is dimmed, the path itself is not`() {
        path(0, 1, 2)
        path(8, 9, 10)
        show()
        controller.selectPath(0)
        rule.waitForIdle()
        assertEquals(listOf(false, false, false), listOf(0, 1, 2).map(::dimmed))
        assertEquals(List(ALL - 3) { true }, (0 until ALL).filter { it !in 0..2 }.map(::dimmed))
    }

    @Test
    fun `with no path selected nothing is dimmed`() {
        path(0, 1, 2)
        show()
        assertEquals(List(ALL) { false }, (0 until ALL).map(::dimmed))
        controller.selectPath(0)
        controller.selectPath(null)
        rule.waitForIdle()
        assertEquals(List(ALL) { false }, (0 until ALL).map(::dimmed))
    }

    @Test
    fun `moving the selection moves the dimming`() {
        path(0, 1, 2)
        path(8, 9, 10)
        show()
        controller.selectPath(0)
        controller.selectPath(1)
        rule.waitForIdle()
        assertEquals(listOf(true, true, true), listOf(0, 1, 2).map(::dimmed))
        assertEquals(listOf(false, false, false), listOf(8, 9, 10).map(::dimmed))
    }

    @Test
    fun `a cell on the live trace is never dimmed`() {
        path(0, 1, 2)
        show()
        controller.selectPath(0)
        controller.traceTo(20)
        rule.waitForIdle()
        assertEquals(false, dimmed(20))
    }

    private companion object {
        const val ALL = StrandsDraft.ROWS * StrandsDraft.COLS
    }
}
