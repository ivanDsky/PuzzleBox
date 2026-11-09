package com.ivandsky.puzzlebox.games.strandsui.editor

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.designsystem.BoardColors
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsBoardSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The editor board mid-drag, the finger held down: the cells it passes stay drawn as trace
 * tokens, and the line, its leaning end included, is never covered by an idle cell's tile.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h900dp")
class StrandsEditorDragRenderTest {

    @get:Rule
    val rule = createComposeRule()

    private val controller = StrandsEditorController(EditorDraft.fresh(StrandsEditor, StrandsDefinition.VARIANT)) {}
    private lateinit var colors: BoardColors

    /** The board for a fresh draft; [reducedMotion] (animator scale 0) draws the lean at once. */
    private fun show(reducedMotion: Boolean = false) {
        if (reducedMotion) {
            val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        }
        rule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                colors = LocalBoardColors.current
                StrandsEditorUi.Board(controller, Verdict.Checking, Modifier)
            }
        }
        rule.waitForIdle()
    }

    /** Presses on (0, 0) and slides through (0, 1), (0, 2) and the diagonal (1, 3), held down. */
    private fun dragFourCells() = rule.onNodeWithTag(BOARD).performTouchInput {
        down(cellPoint(0.5f, 0.5f))
        moveTo(cellPoint(1.5f, 0.5f))
        moveTo(cellPoint(2.5f, 0.5f))
        moveTo(cellPoint(3.5f, 1.5f))
    }

    /** The board pixel [colCells], [rowCells] cell units from the grid's top-left, after 1/4 s. */
    private fun pixelAt(colCells: Float, rowCells: Float): Color = pixelsAlong(rowCells, colCells, colCells).single()

    /** The board pixels on row [rowCells] from [fromCol] to [toCol] cell units, after 1/4 s. */
    private fun pixelsAlong(rowCells: Float, fromCol: Float, toCol: Float): List<Color> {
        repeat(FRAMES) { rule.mainClock.advanceTimeBy(FRAME_MS) }
        val pixels = rule.onNodeWithTag(BOARD).captureToImage().toPixelMap()
        val cell = pixels.height / StrandsDraft.ROWS.toFloat()
        val left = (pixels.width - cell * StrandsDraft.COLS) / 2
        val y = (rowCells * cell).toInt()
        return ((left + fromCol * cell).toInt()..(left + toCol * cell).toInt()).map { pixels[it, y] }
    }

    @Test
    fun `every cell a held drag passes is drawn as a trace token`() {
        show()
        dragFourCells()

        assertEquals(listOf(0, 1, 2, 9), controller.trace.value)
        // Below each centre: the token's face, clear of the line through the centres.
        for ((col, row) in listOf(0 to 0, 1 to 0, 2 to 0, 3 to 1)) {
            assertEquals("cell ($row, $col)", colors.strandsSelectionFill, pixelAt(col + 0.5f, row + 0.78f))
        }
        rule.onNodeWithTag(BOARD).performTouchInput { up() }
    }

    @Test
    fun `the leaning end of a held drag shows over the idle tile it enters`() {
        show(reducedMotion = true)
        dragFourCells()
        // 0.57 cell east of the tail: short of (1, 4)'s join circle, the round end past its edge.
        rule.onNodeWithTag(BOARD).performTouchInput { moveTo(cellPoint(4.07f, 1.5f)) }

        assertEquals(listOf(0, 1, 2, 9), controller.trace.value)
        val tileEdge = 4f + (1f - StrandsBoardSpec.TokenFraction) / 2
        val inTile = pixelsAlong(1.5f, tileEdge + 0.01f, tileEdge + 0.1f)
        assertTrue("the line's end is hidden in $inTile", colors.strandsTraceConnector in inTile)
        rule.onNodeWithTag(BOARD).performTouchInput { up() }
    }

    @Test
    fun `idle tiles are part of the board, under its connectors and letters`() {
        show()
        val board = rule.onNodeWithTag(GRID, useUnmergedTree = true).fetchSemanticsNode()
        val tiles = rule.onAllNodesWithTag(TILE, useUnmergedTree = true).fetchSemanticsNodes()

        assertEquals(StrandsDraft.ROWS * StrandsDraft.COLS, tiles.size)
        tiles.forEach { assertTrue("a tile outside the board grid", it.isInside(board)) }
    }

    private fun SemanticsNode.isInside(ancestor: SemanticsNode): Boolean =
        generateSequence(parent) { it.parent }.any { it.id == ancestor.id }

    private fun TouchInjectionScope.cellPoint(colCells: Float, rowCells: Float): Offset {
        val cell = height / StrandsDraft.ROWS
        return Offset((width - cell * StrandsDraft.COLS) / 2 + colCells * cell, rowCells * cell)
    }

    private companion object {
        const val BOARD = "strands.editor.board"
        const val GRID = "strands.board"
        const val TILE = "strands.editor.cell.empty"
        const val FRAME_MS = 16L
        const val FRAMES = 16
    }
}
