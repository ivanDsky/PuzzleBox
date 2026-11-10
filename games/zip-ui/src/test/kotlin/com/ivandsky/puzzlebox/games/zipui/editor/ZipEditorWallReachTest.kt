package com.ivandsky.puzzlebox.games.zipui.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.editor.ZipDraft
import com.ivandsky.puzzlebox.games.zip.editor.ZipEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A wall tap works along the whole side of an inside edge (M4-zip-19): the part of a side that
 * lies in the band of the crossing line too goes to the nearer line instead of to nothing.
 * Taps are in cell units from the board's top-left; the walls are far from the top-row path.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h900dp-420dpi")
class ZipEditorWallReachTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(n: Int): ZipEditorController {
        val blank = EditorDraft.fresh(ZipEditor, VariantId("zip/n$n"))
        val board = (blank.state.draft as ZipDraft).copy(
            path = List(n) { it },
            waypoints = listOf(0, n - 1),
            walls = emptySet(),
        )
        val controller = ZipEditorController(EditorDraft(ZipEditor, blank.state.copy(draft = board))) {}
        rule.setContent {
            PuzzleBoxTheme { ZipEditorBoard(controller, Modifier.fillMaxSize()) }
        }
        rule.onNodeWithTag("zip.editor.step.walls").performClick()
        rule.waitForIdle()
        return controller
    }

    private fun tapAt(n: Int, x: Float, y: Float) {
        rule.onNodeWithTag(BOARD).performTouchInput { click(Offset(x * width / n, y * height / n)) }
        rule.waitForIdle()
    }

    private fun hasWallTag(tag: String) =
        rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Tapping at ([x], [y]) toggles exactly [wall], with its tag; a second tap removes it. */
    private fun ZipEditorController.assertTapToggles(n: Int, x: Float, y: Float, wall: Wall, tag: String) {
        tapAt(n, x, y)
        assertEquals("n=$n at ($x, $y)", setOf(wall), shown.value.walls)
        assertTrue("n=$n at ($x, $y)", hasWallTag(tag))
        tapAt(n, x, y)
        assertTrue("n=$n at ($x, $y)", shown.value.walls.isEmpty())
    }

    private fun aTapAlongAnySideTogglesItsWall(n: Int) {
        val controller = show(n)
        val row = 3
        val col = 2
        val cell = row * n + col
        // The right side of (row, col) lies on x = col + 1 from y = row to y = row + 1; the
        // crossing lines y = row and y = row + 1 have their bands over both ends of it.
        for (along in listOf(0.3f, 0.5f, 0.7f)) {
            controller.assertTapToggles(
                n, (col + 1).toFloat(), row + along,
                Wall(Cell(cell), WallDirection.RIGHT), "zip.editor.edge.$cell.right",
            )
        }
        // The bottom side of (row, col) lies on y = row + 1 from x = col to x = col + 1.
        for (along in listOf(0.3f, 0.5f, 0.7f)) {
            controller.assertTapToggles(
                n, col + along, (row + 1).toFloat(),
                Wall(Cell(cell), WallDirection.DOWN), "zip.editor.edge.$cell.bottom",
            )
        }
    }

    @Test
    fun `on a 5x5 board a tap 0·3 along a side toggles that side's wall`() {
        aTapAlongAnySideTogglesItsWall(5)
    }

    @Test
    fun `on an 8x8 board a tap 0·3 along a side toggles that side's wall`() {
        aTapAlongAnySideTogglesItsWall(8)
    }

    private companion object {
        const val BOARD = "zip.editor.board"
    }
}
