package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.editor.QueensCrowns
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ticket 36: the board draws answer crowns and their derived ×; the Crown | Paint toggle sits in the hint slot. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h900dp-420dpi")
class QueensCrownLayerTest {

    @get:Rule
    val rule = createComposeRule()

    private fun controller() = QueensEditorController(EditorDraft.fresh(QueensEditor, QueensVariants.variant(SIZE).id)) {}

    private fun show(controller: QueensEditorController) {
        rule.setContent {
            PuzzleBoxTheme {
                Column {
                    QueensEditorUi.ToolHint(controller, Modifier.fillMaxWidth())
                    QueensEditorBoard(controller, Verdict.Checking, Modifier.fillMaxWidth())
                }
            }
        }
    }

    @Test
    fun `one crown tags its cell and exactly the crossed cells carry the auto x`() {
        val controller = controller()
        show(controller)

        rule.onNodeWithTag("queens.editor.cell.$CROWN", useUnmergedTree = true).performClick()

        rule.onAllNodesWithTag("queens.editor.cell.$CROWN.crown", useUnmergedTree = true).assertCountEquals(1)
        val crossed = QueensCrowns.crossed(controller.draft.value.state.draft as QueensDraft)
        assertEquals(SIZE * 2 - 2 + 4, crossed.size)
        for (idx in 0 until SIZE * SIZE) {
            rule.onAllNodesWithTag("queens.editor.cell.$idx.autoX", useUnmergedTree = true)
                .assertCountEquals(if (idx in crossed) 1 else 0)
            if (idx != CROWN) {
                rule.onAllNodesWithTag("queens.editor.cell.$idx.crown", useUnmergedTree = true).assertCountEquals(0)
            }
        }
    }

    @Test
    fun `the hint slot carries the mode toggle and its segments switch the mode`() {
        val controller = controller()
        show(controller)
        rule.onNodeWithTag(CROWN_MODE).assertIsSelected()

        rule.onNodeWithTag(PAINT_MODE).performClick()

        assertEquals(QueensEditorMode.Paint, controller.mode.value)
        rule.onNodeWithTag(PAINT_MODE).assertIsSelected()

        rule.onNodeWithTag(CROWN_MODE).performClick()

        assertEquals(QueensEditorMode.Crown, controller.mode.value)
    }

    @Test
    fun `in Paint a tap selects and crowns stay drawn`() {
        val controller = controller()
        show(controller)
        rule.onNodeWithTag("queens.editor.cell.$CROWN", useUnmergedTree = true).performClick()
        rule.onNodeWithTag(PAINT_MODE).performClick()

        rule.onNodeWithTag("queens.editor.cell.2", useUnmergedTree = true).performClick()

        assertEquals(setOf(2), controller.selection.value)
        rule.onAllNodesWithTag("queens.editor.cell.$CROWN.crown", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithTag("queens.editor.cell.2.autoX", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `an auto x under the other answer's ghost crown is drawn fainter than a plain one`() {
        rule.setContent {
            PuzzleBoxTheme {
                Column {
                    listOf(false, true).forEachIndexed { idx, ghosted ->
                        Box(
                            Modifier
                                .size(CELL_DP.dp)
                                .background(MaterialTheme.colorScheme.surface)
                                .testTag("probe.$idx"),
                        ) {
                            QueensCrownLayer(idx, region = 0, crowned = false, crossed = true, ghosted = ghosted, clash = false, rejection = null, reducedMotion = true)
                        }
                    }
                }
            }
        }

        val plain = ink("probe.0")
        val ghosted = ink("probe.1")

        assertTrue("the × is drawn", ghosted > 0)
        assertTrue("ghosted ink $ghosted is fainter than plain ink $plain", ghosted < plain)
    }

    /** How far the cell's pixels sit from its empty corner: the × is the only thing drawn. */
    private fun ink(tag: String): Long {
        val image = rule.onNodeWithTag(tag).captureToImage().toPixelMap()
        val empty = image[0, 0]
        var total = 0L
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                val pixel = image[x, y]
                total += (abs(pixel.red - empty.red) + abs(pixel.green - empty.green) + abs(pixel.blue - empty.blue) +
                    abs(pixel.alpha - empty.alpha)).times(INK_SCALE).toLong()
            }
        }
        return total
    }

    private companion object {
        const val SIZE = 6
        const val CELL_DP = 60
        const val INK_SCALE = 255f
        const val CROWN = 14 // row 2, column 2: an inner cell with all 8 neighbours
        const val CROWN_MODE = "queens.editor.mode.crown"
        const val PAINT_MODE = "queens.editor.mode.paint"
    }
}
