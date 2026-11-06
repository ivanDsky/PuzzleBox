package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tickets 18–19: select cells (tap, drag, All of colour, Area), pick one ring colour → one Paint step. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h900dp-420dpi")
class QueensEditorControllerTest {

    @get:Rule
    val rule = createComposeRule()

    private val edits = ArrayList<EditorDraft<*>>()

    /** A 6×6 board painted by rows (row r = region r), so every cell starts painted. */
    private fun painted(): EditorDraft<QueensDraft> {
        val blank = EditorDraft.fresh(QueensEditor, QueensVariants.variant(SIZE).id)
        val rows = blank.state.draft.copy(regions = List(SIZE * SIZE) { it / SIZE })
        return EditorDraft(QueensEditor, blank.state.copy(draft = rows))
    }

    private fun controller() = QueensEditorController(painted()) { edits += it }

    private fun regionsOf(draft: EditorDraft<*>): List<Int> = (draft.state.draft as QueensDraft).regions

    @Test
    fun `three selected cells and one pick give exactly one Paint step and an empty selection`() {
        val controller = controller()
        listOf(0, 7, 14).forEach(controller::tap)
        assertEquals(setOf(0, 7, 14), controller.selection.value)

        controller.pick(4)

        assertEquals(1, edits.size)
        val regions = regionsOf(edits.single())
        listOf(0, 7, 14).forEach { assertEquals(4, regions[it]) }
        assertEquals(regionsOf(painted()).filterIndexed { i, _ -> i !in setOf(0, 7, 14) },
            regions.filterIndexed { i, _ -> i !in setOf(0, 7, 14) })
        assertEquals(emptySet<Int>(), controller.selection.value)
        assertEquals(edits.single(), controller.draft.value)
        assertEquals(regionsOf(painted()), regionsOf(edits.single().undo()!!))
        assertTrue(edits.single().undo()!!.canUndo.not())
    }

    @Test
    fun `a tap toggles a cell and cancel clears the selection without an edit`() {
        val controller = controller()
        controller.tap(3)
        controller.tap(5)
        controller.tap(3)
        assertEquals(setOf(5), controller.selection.value)

        controller.cancel()

        assertEquals(emptySet<Int>(), controller.selection.value)
        assertEquals(0, edits.size)
    }

    @Test
    fun `a pick with nothing selected is not an edit`() {
        val controller = controller()
        controller.pick(2)
        assertEquals(0, edits.size)
    }

    @Test
    fun `a tap on a cell opens the ring at once`() {
        val controller = controller()
        show(controller)
        rule.onAllNodesWithTag(RING).assertCountEquals(0)

        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("queens.editor.cell.0", useUnmergedTree = true).performClick()
        rule.mainClock.advanceTimeByFrame()

        rule.onAllNodesWithTag(RING).assertCountEquals(1)
        assertEquals(setOf(0), controller.selection.value)
        assertEquals(0, edits.size)
    }

    @Test
    fun `picking the ring's slot paints the selection in one step`() {
        val controller = controller()
        show(controller)
        listOf(0, 7, 14).forEach(controller::tap)

        rule.onNodeWithTag("queens.editor.ring.slot.4").performClick()

        assertEquals(1, edits.size)
        listOf(0, 7, 14).forEach { assertEquals(4, regionsOf(edits.single())[it]) }
        rule.onAllNodesWithTag(RING).assertCountEquals(0)
    }

    @Test
    fun `picking the ring's unpaint centre unpaints the selection`() {
        val controller = controller()
        show(controller)
        listOf(0, 7, 14).forEach(controller::tap)

        rule.onNodeWithTag(UNPAINT).performClick()

        assertEquals(1, edits.size)
        listOf(0, 7, 14).forEach {
            assertEquals(QueensDraft.UNPAINTED, regionsOf(edits.single())[it])
            rule.onNodeWithTag("queens.editor.cell.$it", useUnmergedTree = true)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, UNPAINTED_DESCRIPTION))
        }
        assertEquals(emptySet<Int>(), controller.selection.value)
    }

    @Test
    fun `selectAllOf grows the selection to exactly that region's cells without an edit`() {
        val controller = controller()
        controller.tap(20)

        controller.selectAllOf(3)

        assertEquals((3 * SIZE until 4 * SIZE).toSet(), controller.selection.value)
        assertEquals(0, edits.size)
    }

    @Test
    fun `selectArea selects the bounding rectangle from the first cell to the last`() {
        val controller = controller()

        controller.selectArea(0, 2 * SIZE + 2)

        assertEquals(setOf(0, 1, 2, 6, 7, 8, 12, 13, 14), controller.selection.value)
        controller.cancel()
        controller.selectArea(2 * SIZE + 2, 0)
        assertEquals(setOf(0, 1, 2, 6, 7, 8, 12, 13, 14), controller.selection.value)
        assertEquals(0, edits.size)
    }

    @Test
    fun `the context bar counts the selection and its chips grow or cancel it`() {
        val controller = controller()
        show(controller)
        rule.onAllNodesWithTag(COUNT).assertCountEquals(0)
        rule.onNodeWithTag(HINT).assertExists()

        controller.tap(0)
        controller.tap(2 * SIZE + 2)
        rule.onNodeWithTag(COUNT).assert(count(2))

        rule.onNodeWithTag(AREA).performClick()
        assertEquals(setOf(0, 1, 2, 6, 7, 8, 12, 13, 14), controller.selection.value)
        rule.onNodeWithTag(COUNT).assert(count(9))
        rule.onAllNodesWithTag(RING).assertCountEquals(1)

        rule.onNodeWithTag(ALL_OF).performClick()
        assertEquals((0 until 3 * SIZE).toSet(), controller.selection.value)
        rule.onNodeWithTag(COUNT).assert(count(3 * SIZE))

        rule.onNodeWithTag(CANCEL).performClick()
        assertEquals(emptySet<Int>(), controller.selection.value)
        rule.onAllNodesWithTag(COUNT).assertCountEquals(0)
        rule.onAllNodesWithTag(RING).assertCountEquals(0)
        assertEquals(0, edits.size)
    }

    private fun count(k: Int) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "$k")

    private fun show(controller: QueensEditorController) {
        rule.setContent {
            PuzzleBoxTheme { QueensEditorBoard(controller, Verdict.Checking, Modifier.fillMaxWidth()) }
        }
    }

    private companion object {
        const val SIZE = 6
        const val RING = "queens.editor.ring"
        const val UNPAINT = "queens.editor.ring.unpaint"
        const val COUNT = "queens.editor.context.count"
        const val HINT = "queens.editor.context.hint"
        const val AREA = "queens.editor.chip.area"
        const val ALL_OF = "queens.editor.chip.allOf"
        const val CANCEL = "queens.editor.chip.cancel"
    }
}
