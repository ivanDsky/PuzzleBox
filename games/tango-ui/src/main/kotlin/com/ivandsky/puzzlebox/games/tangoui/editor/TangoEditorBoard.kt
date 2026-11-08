package com.ivandsky.puzzlebox.games.tangoui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraft
import com.ivandsky.puzzlebox.games.tango.editor.TangoEditor
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import com.ivandsky.puzzlebox.gameui.GridEdge
import com.ivandsky.puzzlebox.gameui.GridGeometry
import com.ivandsky.puzzlebox.games.tangoui.TangoBoardRenderer
import com.ivandsky.puzzlebox.games.tangoui.TangoBoardSpec

/** Widest the editor board grows, so a tablet pane does not stretch the cells. */
private val EditorBoardMaxWidth = 480.dp

/**
 * The editor's board: the play renderer over the [controller]'s draft, every cell a free
 * given (no cell is inert). Touch is [tangoEditorGestures]: pressing a square or an edge
 * shows its next state (the square `.preview`, the edge `tango.editor.edge.<cell>.<side>`
 * band) and lifting commits it as one step. Markers show as on the play board.
 */
@Composable
internal fun TangoEditorBoard(controller: TangoEditorController, modifier: Modifier = Modifier) {
    val draft by controller.draft.collectAsState()
    val pressed by controller.preview.collectAsState()
    val board = draft.state.draft as TangoDraft
    val target = pressed
    val shown = if (target == null) board else controller.commandFor(target).apply(board)
    val model = remember(shown) { modelOf(shown) }
    val renderer = remember { TangoBoardRenderer() }
    Box(
        modifier
            .fillMaxWidth()
            .widthIn(max = EditorBoardMaxWidth)
            .testTag("tango.editor.board")
            .tangoEditorGestures(controller),
    ) {
        renderer.Content(model = model, modifier = Modifier.fillMaxWidth())
        if (target != null) PressWash(target)
    }
}

/** A faint wash over the pressed square or the pressed edge's hit band. */
@Composable
private fun BoxScope.PressWash(target: TangoEditorTarget) {
    BoxWithConstraints(Modifier.matchParentSize()) {
        val pitch = maxWidth / TangoDraft.N
        val across = TangoBoardSpec.editEdgeBandAcrossFraction
        val inset = TangoBoardSpec.editEdgeBandInsetFraction
        val wash = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = TangoBoardSpec.editPressWashAlpha))
        val (tag, frame) = when (target) {
            is TangoEditorTarget.Square -> {
                val row = target.cell / TangoDraft.N
                val col = target.cell % TangoDraft.N
                "tango.editor.cell.${target.cell}.preview" to
                    Modifier.offset(pitch * col, pitch * row).size(pitch)
            }
            is TangoEditorTarget.Edge -> {
                val cell = target.edge.cell
                val idx = cell.row * TangoDraft.N + cell.col
                when (target.edge.side) {
                    GridEdge.Side.Right -> "tango.editor.edge.$idx.right" to
                        Modifier
                            .offset(pitch * (cell.col + 1 - across), pitch * (cell.row + inset))
                            .size(pitch * 2 * across, pitch * (1 - 2 * inset))
                    GridEdge.Side.Bottom -> "tango.editor.edge.$idx.bottom" to
                        Modifier
                            .offset(pitch * (cell.col + inset), pitch * (cell.row + 1 - across))
                            .size(pitch * (1 - 2 * inset), pitch * 2 * across)
                }
            }
        }
        Box(frame.then(wash).testTag(tag))
    }
}

/**
 * Press previews, lift commits, and moving past the touch slop cancels. What is under the
 * finger is the edge [GridGeometry.edgeAt] finds at the [TangoBoardSpec] band and inset,
 * else the square.
 */
private fun Modifier.tangoEditorGestures(controller: TangoEditorController): Modifier =
    pointerInput(controller) {
        val n = TangoDraft.N
        val geometry = GridGeometry(n, n, size.width.toFloat(), size.height.toFloat())
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val edge = geometry.edgeAt(
                down.position,
                TangoBoardSpec.editEdgeBandAcrossFraction,
                TangoBoardSpec.editEdgeBandInsetFraction,
            )
            val cell = geometry.cellAt(down.position)
            controller.press(
                if (edge != null) TangoEditorTarget.Edge(edge) else TangoEditorTarget.Square(cell.row * n + cell.col),
            )
            while (true) {
                val change = awaitPointerEvent().changes.first { it.id == down.id }
                when {
                    change.changedToUp() -> { controller.release(); break }
                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop -> {
                        controller.cancelPress()
                        while (!awaitPointerEvent().changes.all { !it.pressed }) Unit
                        break
                    }
                }
            }
        }
    }

private fun modelOf(board: TangoDraft): TangoRendererModel = TangoRendererModel(
    n = TangoDraft.N,
    cells = List(TangoDraft.N * TangoDraft.N) { TangoRendererModel.CellRender(board.givens[it], isGiven = false) },
    markers = board.markers
        .sortedWith(compareBy({ it.cell }, { it.side.ordinal }))
        .map { TangoRendererModel.MarkerRender(it.cell, it.side, it.kind) },
    rejection = null,
    status = TangoRendererModel.Status.IDLE,
)

@PuzzleBoxPreviews
@Preview(name = "Tango editor board", widthDp = 360, heightDp = 420, showBackground = true)
@Composable
private fun TangoEditorBoardPreview() {
    val blank = EditorDraft.fresh(TangoEditor, TangoEditor.variants.first())
    val givens = mapOf(0 to TangoCell.SUN, 1 to TangoCell.MOON, 14 to TangoCell.SUN)
    val draft = EditorDraft(TangoEditor, blank.state.copy(draft = blank.state.draft.copy(givens = givens)))
    PuzzleBoxTheme { TangoEditorBoard(TangoEditorController(draft) {}) }
}
