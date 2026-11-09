package com.ivandsky.puzzlebox.games.tangoui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraft
import com.ivandsky.puzzlebox.games.tango.editor.TangoEditor
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import com.ivandsky.puzzlebox.gameui.GridEdge
import com.ivandsky.puzzlebox.gameui.GridGeometry
import com.ivandsky.puzzlebox.games.tangoui.TangoBoardRenderer
import com.ivandsky.puzzlebox.games.tangoui.TangoBoardSpec

/**
 * The editor's board: the play renderer over the [controller]'s draft, every cell a free
 * given (no cell is inert). Touch is [tangoEditorGestures]: pressing a square or an edge
 * shows its next state (the square `.preview`, the edge `tango.editor.edge.<cell>.<side>`
 * band) and lifting commits it as one step. Markers show as on the play board. The
 * [verdict] and the card's witness choice ([TangoEditorController.overlay]) are drawn by
 * [TangoDiagnosticsLayer]; [TangoEditorController.forcedView] adds the forced cells as
 * faint symbols ([TangoForcedLayer]), under the witness, never taking a tap.
 */
@Composable
internal fun TangoEditorBoard(
    controller: TangoEditorController,
    modifier: Modifier = Modifier,
    verdict: Verdict = Verdict.Checking,
) {
    val draft by controller.draft.collectAsState()
    val pressed by controller.preview.collectAsState()
    val overlay by controller.overlay.collectAsState()
    val zones by controller.tapZones.collectAsState()
    val forcedOn by controller.forcedView.collectAsState()
    val board = draft.state.draft as TangoDraft
    val target = pressed
    val shown = if (target == null) board else controller.commandFor(target).apply(board)
    // The last settled verdict, so the witness symbols hold while a new analysis runs.
    val settled = remember { mutableStateOf<Verdict?>(null) }
    SideEffect { if (verdict != Verdict.Checking) settled.value = verdict }
    val held = if (verdict == Verdict.Checking) settled.value else verdict
    val diagnostics = tangoDiagnostics(shown, verdict, held, overlay)
    val forced by rememberForcedCells(board, forcedOn)
    val ghosts = remember(forced, diagnostics.witness) { forced + diagnostics.witness }
    val model = remember(shown, ghosts) { modelOf(shown, ghosts) }
    val renderer = remember { TangoBoardRenderer() }
    Box(
        Modifier
            .then(modifier)
            .fillMaxWidth()
            .testTag("tango.editor.board")
            .tangoEditorGestures(controller),
    ) {
        renderer.Content(model = model, modifier = Modifier.fillMaxWidth())
        TangoForcedLayer(forced.keys, board.n)
        TangoDiagnosticsLayer(diagnostics, board.n)
        if (zones) TapZoneBands(board.n)
        if (target != null) PressWash(target, board.n)
    }
}

/** Outlines every interior edge's hit band, so the author sees where a tap lands. */
@Composable
private fun BoxScope.TapZoneBands(n: Int) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.matchParentSize().testTag("tango.editor.tapZones.bands")) {
        val pitch = size.width / n
        val across = TangoBoardSpec.editEdgeBandAcrossFraction
        val inset = TangoBoardSpec.editEdgeBandInsetFraction
        val stroke = Stroke(width = 1.dp.toPx())
        for (row in 0 until n) {
            for (col in 0 until n) {
                if (col < n - 1) {
                    drawRect(
                        color,
                        Offset(pitch * (col + 1 - across), pitch * (row + inset)),
                        Size(pitch * 2 * across, pitch * (1 - 2 * inset)),
                        style = stroke,
                    )
                }
                if (row < n - 1) {
                    drawRect(
                        color,
                        Offset(pitch * (col + inset), pitch * (row + 1 - across)),
                        Size(pitch * (1 - 2 * inset), pitch * 2 * across),
                        style = stroke,
                    )
                }
            }
        }
    }
}

/** A faint wash over the pressed square or the pressed edge's hit band. */
@Composable
private fun BoxScope.PressWash(target: TangoEditorTarget, n: Int) {
    BoxWithConstraints(Modifier.matchParentSize()) {
        val pitch = maxWidth / n
        val across = TangoBoardSpec.editEdgeBandAcrossFraction
        val inset = TangoBoardSpec.editEdgeBandInsetFraction
        val wash = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = TangoBoardSpec.editPressWashAlpha))
        val (tag, frame) = when (target) {
            is TangoEditorTarget.Square -> {
                val row = target.cell / n
                val col = target.cell % n
                "tango.editor.cell.${target.cell}.preview" to
                    Modifier.offset(pitch * col, pitch * row).size(pitch)
            }
            is TangoEditorTarget.Edge -> {
                val cell = target.edge.cell
                val idx = cell.row * n + cell.col
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
 * Press previews, lift commits, and moving past the touch slop cancels. The geometry and the
 * board's width are read per gesture so a resize, a rotation or a new draft never leaves them
 * stale. What is under the finger is the edge [GridGeometry.edgeAt] finds at the
 * [TangoBoardSpec] band and inset, else the square.
 */
private fun Modifier.tangoEditorGestures(controller: TangoEditorController): Modifier =
    pointerInput(controller) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val n = controller.n
            val geometry = GridGeometry(n, n, size.width.toFloat(), size.height.toFloat())
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

private fun modelOf(board: TangoDraft, witness: Map<Int, TangoCell>): TangoRendererModel = TangoRendererModel(
    n = board.n,
    cells = List(board.n * board.n) { TangoRendererModel.CellRender(board.givens[it], isGiven = false) },
    markers = board.markers
        .sortedWith(compareBy({ it.cell }, { it.side.ordinal }))
        .map { TangoRendererModel.MarkerRender(it.cell, it.side, it.kind) },
    rejection = null,
    status = TangoRendererModel.Status.IDLE,
    ghosts = witness,
)

@PuzzleBoxPreviews
@Preview(name = "Tango editor board", widthDp = 360, heightDp = 420, showBackground = true)
@Composable
private fun TangoEditorBoardPreview() {
    val blank = EditorDraft.fresh(TangoEditor, TangoEditor.defaultVariant)
    val givens = mapOf(0 to TangoCell.SUN, 1 to TangoCell.MOON, 14 to TangoCell.SUN)
    val draft = EditorDraft(TangoEditor, blank.state.copy(draft = blank.state.draft.copy(givens = givens)))
    PuzzleBoxTheme { TangoEditorBoard(TangoEditorController(draft) {}) }
}
