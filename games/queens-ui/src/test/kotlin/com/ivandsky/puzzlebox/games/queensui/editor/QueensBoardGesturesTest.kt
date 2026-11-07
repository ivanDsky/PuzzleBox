package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ticket 37: a drag out from a coloured cell strokes its colour; from an unpainted cell, Paint still drag-selects. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h900dp-420dpi")
class QueensBoardGesturesTest {

    @get:Rule
    val rule = createComposeRule()

    private val edits = ArrayList<EditorDraft<*>>()

    private fun controller() = QueensEditorController(EditorDraft.fresh(QueensEditor, QueensVariants.variant(SIZE).id)) { edits += it }

    private fun show(controller: QueensEditorController) {
        rule.setContent {
            PuzzleBoxTheme { QueensEditorBoard(controller, Verdict.Checking, Modifier.fillMaxWidth()) }
        }
    }

    private fun TouchInjectionScope.centre(cell: Int) = Offset(
        x = (cell % SIZE + 0.5f) * width / SIZE,
        y = (cell / SIZE + 0.5f) * height / SIZE,
    )

    /** Presses on [from] and moves in small steps to [to], without lifting. */
    private fun press(from: Int, to: Int) {
        rule.onNodeWithTag(BOARD).performTouchInput {
            val start = centre(from)
            val end = centre(to)
            down(start)
            for (step in 1..STEPS) moveTo(start + (end - start) * (step / STEPS.toFloat()))
        }
    }

    private fun lift() {
        rule.onNodeWithTag(BOARD).performTouchInput { up() }
    }

    private fun regionsOf(draft: EditorDraft<*>): List<Int> = (draft.state.draft as QueensDraft).regions

    @Test
    fun `in Crown a swipe from a crown previews the stroke and paints its colour on lift`() {
        val controller = controller()
        show(controller)
        rule.onNodeWithTag("queens.editor.cell.$CROWN", useUnmergedTree = true).performClick()
        val seed = regionsOf(controller.draft.value)[CROWN]
        edits.clear()

        press(CROWN, CROWN + 3)

        STROKED.forEach { rule.onAllNodesWithTag("queens.editor.cell.$it.stroke", useUnmergedTree = true).assertCountEquals(1) }
        rule.onAllNodesWithTag("queens.editor.cell.$CROWN.stroke", useUnmergedTree = true).assertCountEquals(0)
        assertEquals(0, edits.size)

        lift()

        assertEquals(1, edits.size)
        STROKED.forEach {
            assertEquals(seed, regionsOf(controller.draft.value)[it])
            rule.onAllNodesWithTag("queens.editor.cell.$it.stroke", useUnmergedTree = true).assertCountEquals(0)
        }
        assertEquals(QueensEditorMode.Crown, controller.mode.value)
        assertEquals(emptySet<Int>(), controller.selection.value)
    }

    @Test
    fun `in Paint a swipe from an unpainted cell selects what it crosses`() {
        val controller = controller()
        controller.setMode(QueensEditorMode.Paint)
        show(controller)

        press(0, 2)
        lift()

        assertEquals(setOf(0, 1, 2), controller.selection.value)
        rule.onNodeWithTag(COUNT).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3"))
        rule.onAllNodesWithTag("queens.editor.cell.1.stroke", useUnmergedTree = true).assertCountEquals(0)
        assertEquals(0, edits.size)
    }

    @Test
    fun `a stroke takes the colour of the cell under the pointer-down, not where the slop is crossed`() {
        val blank = EditorDraft.fresh(QueensEditor, QueensVariants.variant(SIZE).id)
        val rows = EditorDraft(QueensEditor, blank.state.copy(draft = blank.state.draft.copy(regions = List(SIZE * SIZE) { it / SIZE })))
        val controller = QueensEditorController(rows) { edits += it }
        show(controller)

        rule.onNodeWithTag(BOARD).performTouchInput {
            val down = Offset(centre(0).x, height / SIZE - 2f) // cell 0 (region 0), just above row 1
            down(down)
            moveTo(centre(SIZE)) // one move straight across the slop into cell 6 (region 1)
            moveTo(centre(2 * SIZE))
            up()
        }

        assertEquals(1, edits.size)
        val regions = regionsOf(controller.draft.value)
        listOf(0, SIZE, 2 * SIZE).forEach { assertEquals(0, regions[it]) }
    }

    private companion object {
        const val SIZE = 6
        const val CROWN = 12 // row 2, column 0: three empty cells to its right
        val STROKED = listOf(13, 14, 15)
        const val STEPS = 30
        const val BOARD = "queens.editor.board"
        const val COUNT = "queens.editor.context.count"
    }
}
