package com.ivandsky.puzzlebox.games.zipui.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipBoard as ZipBoardModel
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import com.ivandsky.puzzlebox.games.zip.editor.ZipDraft
import com.ivandsky.puzzlebox.games.zip.editor.ZipEditor
import com.ivandsky.puzzlebox.games.zip.engine.ZipState
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel
import com.ivandsky.puzzlebox.games.zipui.ZipBoard
import com.ivandsky.puzzlebox.gameui.GridGeometry
import kotlin.math.ceil
import kotlin.math.max

/**
 * The Zip editor's board: the play board's look (dots, pipe, outline) over the controller's
 * [ZipEditorController.shown] draft, with a stroke gesture that draws the author's path.
 */
@Composable
fun ZipEditorBoard(controller: ZipEditorController, modifier: Modifier = Modifier) {
    val board by controller.shown.collectAsState()
    val model = remember(board) { rendererModel(board) }
    Box(modifier.testTag("zip.editor.board")) {
        ZipBoard(model, Modifier.fillMaxWidth())
        Box(Modifier.matchParentSize().strokeGestures(controller, board.n))
    }
}

private fun rendererModel(board: ZipDraft): ZipRendererModel {
    val cells = board.waypoints.map(::Cell)
    val puzzle = ZipPuzzle(ZipEditor.variantOf(board), ZipBoardModel(board.n, board.walls, cells), solution = null)
    return ZipRendererModel.of(ZipState(puzzle, board.path.map(::Cell)), null)
}

/** Down begins a stroke, each sample walks the cells between it and the last one, up commits. */
private fun Modifier.strokeGestures(controller: ZipEditorController, n: Int): Modifier =
    pointerInput(controller, n) {
        awaitEachGesture {
            val geometry = GridGeometry(n, n, size.width.toFloat(), size.height.toFloat())
            val down = awaitFirstDown(requireUnconsumed = false)
            controller.beginStroke(cellOf(geometry, down.position, n))
            var last = down.position
            do {
                val change = awaitPointerEvent().changes.firstOrNull() ?: break
                val samples = change.historical.map { it.position } + change.position
                samples.forEach { sample ->
                    walk(geometry, last, sample).forEach { cell -> controller.strokeTo(cell.row * n + cell.col) }
                    last = sample
                }
                change.consume()
            } while (change.pressed)
            controller.endStroke()
        }
    }

private fun cellOf(geometry: GridGeometry, at: Offset, n: Int): Int =
    geometry.cellAt(at).let { it.row * n + it.col }

/** Cells entered going from [from] to [to], sampled at under half a cell so a fast swipe leaves no gaps. */
private fun walk(geometry: GridGeometry, from: Offset, to: Offset) =
    (to - from).let { delta ->
        val steps = max(1, ceil(delta.getDistance() / (minOf(geometry.cellWidth, geometry.cellHeight) / 2)).toInt())
        (1..steps).map { geometry.cellAt(from + delta * (it / steps.toFloat())) }.distinct()
    }

@PuzzleBoxPreviews
@Preview(name = "Zip editor, drawn path", widthDp = 360, heightDp = 420, showBackground = true)
@Composable
private fun ZipEditorBoardPathPreview() {
    PuzzleBoxTheme { ZipEditorBoard(previewController(path = listOf(0, 1, 2, 7, 12, 11, 10)), Modifier.fillMaxWidth()) }
}

@PuzzleBoxPreviews
@Preview(name = "Zip editor, empty", widthDp = 360, heightDp = 420, showBackground = true)
@Composable
private fun ZipEditorBoardEmptyPreview() {
    PuzzleBoxTheme { ZipEditorBoard(previewController(path = emptyList()), Modifier.fillMaxWidth()) }
}

private fun previewController(path: List<Int>): ZipEditorController {
    val blank = EditorDraft.fresh(ZipEditor, ZipEditor.variants.first())
    val drawn = blank.state.draft.copy(path = path, waypoints = listOfNotNull(path.firstOrNull(), path.lastOrNull()))
    return ZipEditorController(EditorDraft(ZipEditor, blank.state.copy(draft = drawn))) {}
}
