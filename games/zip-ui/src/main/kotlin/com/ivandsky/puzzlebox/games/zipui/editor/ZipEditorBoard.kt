package com.ivandsky.puzzlebox.games.zipui.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
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
 * [ZipEditorController.shown] draft, with a stroke gesture that draws the author's path in the
 * Path step, and the step tabs and actions ([ZipEditorTools]) below the grid.
 */
@Composable
fun ZipEditorBoard(controller: ZipEditorController, modifier: Modifier = Modifier) {
    val board by controller.shown.collectAsState()
    val model = remember(board) { rendererModel(board) }
    val step by controller.step.collectAsState()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val shake = shakeOffset(controller)
        Box(
            Modifier.weight(1f, fill = false)
                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                .graphicsLayer { translationX = shake.value }
                .testTag("zip.editor.board"),
        ) {
            ZipBoard(model, Modifier.fillMaxSize())
            CellTags(board)
            when (step) {
                ZipEditorStep.Path -> Box(Modifier.matchParentSize().strokeGestures(controller, board.n))
                ZipEditorStep.Numbers -> Box(Modifier.matchParentSize().tapGestures(controller, board.n))
                ZipEditorStep.Walls -> Box(Modifier.matchParentSize().edgeGestures(controller, board.n))
            }
        }
        ZipEditorTools(controller)
    }
}

/**
 * An invisible grid of cells, each tagged `zip.editor.cell.<idx>`, over the drawn board. A numbered
 * cell holds a child tagged `zip.editor.cell.<idx>.number` that reads its label, and a cell with
 * a wall on its right or bottom side a child tagged `zip.editor.edge.<idx>.<right|bottom>`.
 */
@Composable
private fun CellTags(board: ZipDraft) {
    val n = board.n
    Column(Modifier.fillMaxSize()) {
        repeat(n) { row ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                repeat(n) { col ->
                    val cell = row * n + col
                    Box(Modifier.weight(1f).fillMaxHeight().testTag("zip.editor.cell.$cell")) {
                        val label = board.waypoints.indexOf(cell) + 1
                        if (label > 0) {
                            Box(
                                Modifier.matchParentSize()
                                    .semantics { text = AnnotatedString(label.toString()) }
                                    .testTag("zip.editor.cell.$cell.number"),
                            )
                        }
                        WallDirection.entries.forEach { direction ->
                            if (Wall(Cell(cell), direction) in board.walls) {
                                val side = direction.name.lowercase().replace("down", "bottom")
                                Box(Modifier.matchParentSize().testTag("zip.editor.edge.$cell.$side"))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A sideways jolt that settles, replayed for each refused number tap. */
@Composable
private fun shakeOffset(controller: ZipEditorController): Animatable<Float, *> {
    val shaken by controller.shaken.collectAsState()
    val reduced = rememberReducedMotion()
    val offset = remember { Animatable(0f) }
    val reach = with(LocalDensity.current) { SHAKE_DP.dp.toPx() }
    LaunchedEffect(shaken) {
        if (shaken == null || reduced) return@LaunchedEffect
        offset.animateTo(
            0f,
            keyframes {
                durationMillis = MotionTokens.DurationMedium
                -reach at MotionTokens.DurationMedium / 4
                reach * 0.8f at MotionTokens.DurationMedium / 2
                -reach * 0.4f at MotionTokens.DurationMedium * 3 / 4
            },
        )
    }
    return offset
}

private const val SHAKE_DP = 5f

/** A tap numbers the cell it lands on. */
private fun Modifier.tapGestures(controller: ZipEditorController, n: Int): Modifier =
    pointerInput(controller, n) {
        val geometry = GridGeometry(n, n, size.width.toFloat(), size.height.toFloat())
        detectTapGestures { controller.tapNumber(cellOf(geometry, it, n)) }
    }

/** A tap near the line between two cells toggles the wall there. */
private fun Modifier.edgeGestures(controller: ZipEditorController, n: Int): Modifier =
    pointerInput(controller, n) {
        val geometry = GridGeometry(n, n, size.width.toFloat(), size.height.toFloat())
        detectTapGestures {
            geometry.edgeAt(it, ZipEditorDefaults.EDGE_BAND, ZipEditorDefaults.EDGE_INSET)?.let(controller::tapEdge)
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
    PuzzleBoxTheme { ZipEditorBoard(previewController(path = listOf(0, 1, 2, 7, 12, 11, 10)), Modifier.fillMaxSize()) }
}

@PuzzleBoxPreviews
@Preview(name = "Zip editor, empty", widthDp = 360, heightDp = 420, showBackground = true)
@Composable
private fun ZipEditorBoardEmptyPreview() {
    PuzzleBoxTheme { ZipEditorBoard(previewController(path = emptyList()), Modifier.fillMaxSize()) }
}

private fun previewController(path: List<Int>): ZipEditorController {
    val blank = EditorDraft.fresh(ZipEditor, ZipEditor.variants.first())
    val drawn = blank.state.draft.copy(path = path, waypoints = listOfNotNull(path.firstOrNull(), path.lastOrNull()))
    return ZipEditorController(EditorDraft(ZipEditor, blank.state.copy(draft = drawn))) {}
}
