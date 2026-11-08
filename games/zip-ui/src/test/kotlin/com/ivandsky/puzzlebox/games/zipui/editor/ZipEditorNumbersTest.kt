package com.ivandsky.puzzlebox.games.zipui.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.zip.editor.ZipDraft
import com.ivandsky.puzzlebox.games.zip.editor.ZipDraftCommand
import com.ivandsky.puzzlebox.games.zip.editor.ZipEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Numbers step through its tags: tap a path cell to number it or remove its number, numbers
 * follow path order, the ends stay put, off-path taps do nothing; each tap is one undo step.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h900dp-420dpi")
class ZipEditorNumbersTest {

    @get:Rule
    val rule = createComposeRule()

    private val edits = ArrayList<EditorDraft<*>>()

    /** The path 0, 1, 2, 3, 4 along the top row, shown in the Numbers step. */
    private fun show(marks: List<Int> = emptyList()): ZipEditorController {
        val blank = EditorDraft.fresh(ZipEditor, ZipEditor.variants.first())
        val path = listOf(0, 1, 2, 3, 4)
        val board = (blank.state.draft as ZipDraft).copy(path = path, waypoints = listOf(0) + marks + 4)
        val controller = ZipEditorController(EditorDraft(ZipEditor, blank.state.copy(draft = board))) { edits += it }
        rule.setContent {
            PuzzleBoxTheme { ZipEditorBoard(controller, Modifier.fillMaxSize()) }
        }
        rule.onNodeWithTag("zip.editor.step.numbers").performClick()
        rule.waitForIdle()
        return controller
    }

    private fun TouchInjectionScope.centre(cell: Int) = Offset(
        x = (cell % SIZE + 0.5f) * width / SIZE,
        y = (cell / SIZE + 0.5f) * height / SIZE,
    )

    private fun tap(cell: Int) {
        rule.onNodeWithTag(BOARD).performTouchInput { click(centre(cell)) }
        rule.waitForIdle()
    }

    private fun assertLabel(cell: Int, label: String) =
        rule.onNodeWithTag("zip.editor.cell.$cell.number", useUnmergedTree = true).assertTextEquals(label)

    private fun assertNoLabel(cell: Int) =
        assertTrue(
            rule.onAllNodes(hasTestTag("zip.editor.cell.$cell.number"), useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )

    private fun board(draft: EditorDraft<*>) = draft.state.draft as ZipDraft

    @Test
    fun `the ends carry 1 and last`() {
        show()

        assertLabel(0, "1")
        assertLabel(4, "2")
    }

    @Test
    fun `tapping a path cell numbers it`() {
        val controller = show()
        tap(2)

        assertEquals(listOf(0, 2, 4), controller.shown.value.waypoints)
        assertLabel(2, "2")
        assertLabel(4, "3")
    }

    @Test
    fun `numbers run in path order whatever order they were placed`() {
        show()
        tap(3)
        tap(1)

        assertLabel(0, "1")
        assertLabel(1, "2")
        assertLabel(3, "3")
        assertLabel(4, "4")
    }

    @Test
    fun `tapping a numbered cell removes its number and renumbers the rest`() {
        val controller = show(marks = listOf(1, 3))
        tap(1)

        assertNoLabel(1)
        assertLabel(3, "2")
        assertLabel(4, "3")
        assertEquals(listOf(0, 3, 4), controller.shown.value.waypoints)
    }

    @Test
    fun `a tap on either end changes nothing and shakes`() {
        val controller = show()
        assertNull(controller.shaken.value)

        tap(0)
        assertEquals(0, controller.shaken.value?.cell)
        tap(4)
        assertEquals(4, controller.shaken.value?.cell)

        assertTrue(edits.isEmpty())
        assertEquals(listOf(0, 4), controller.shown.value.waypoints)
    }

    @Test
    fun `two taps on the same end both shake and commit nothing`() {
        val controller = show()
        tap(0)
        val first = controller.shaken.value
        tap(0)
        val second = controller.shaken.value

        assertNotNull(first)
        assertNotNull(second)
        assertNotSame(first, second)
        assertEquals(0, second?.cell)
        assertTrue(edits.isEmpty())
    }

    @Test
    fun `an off path tap is ignored`() {
        val controller = show()
        tap(12)

        assertNull(controller.shaken.value)
        assertNoLabel(12)
        assertTrue(edits.isEmpty())
    }

    @Test
    fun `each tap is one undo step`() {
        show()
        tap(2)
        tap(2)

        assertEquals(2, edits.size)
        val first = edits.first()
        assertTrue(first.state.history.done.single() is ZipDraftCommand.ToggleMark)
        assertEquals(listOf(0, 4), board(first.undo()!!).waypoints)
        assertEquals(2, edits.last().state.history.done.size)
        assertEquals(listOf(0, 2, 4), board(edits.last().undo()!!).waypoints)
        assertNotNull(edits.last().undo())
    }

    @Test
    fun `taps in the Path step number nothing`() {
        val controller = show()
        rule.onNodeWithTag("zip.editor.step.path").performClick()
        tap(2)

        assertEquals(listOf(0, 4), controller.shown.value.waypoints)
    }

    private companion object {
        const val SIZE = 5
        const val BOARD = "zip.editor.board"
    }
}
