package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import com.ivandsky.puzzlebox.games.queensui.LocalQueensReducedMotion
import com.ivandsky.puzzlebox.games.queensui.QueensBoardModel
import com.ivandsky.puzzlebox.games.queensui.QueensCell
import com.ivandsky.puzzlebox.games.queensui.QueensCellVisual
import com.ivandsky.puzzlebox.games.queensui.QueensRegionPalette
import com.ivandsky.puzzlebox.games.queensui.drawCellEdges
import com.ivandsky.puzzlebox.gameui.BoardGrid

/** What a cell's semantics say when no region is painted on it yet. */
internal const val UNPAINTED_DESCRIPTION = "unpainted"

/**
 * The editor's square board: the play board's region fills and edges over the
 * [controller]'s draft, with an unpainted cell left on the surface tone. A tap paints the
 * cell under it (edge-clamped, as in play). Each cell says its region's hue in its state
 * description, so tests read the painting without pixels.
 */
@Composable
internal fun QueensEditorBoard(controller: QueensEditorController, modifier: Modifier = Modifier) {
    val draft by controller.draft.collectAsState()
    val board = draft.state.draft as QueensDraft
    val model = remember(board) { modelOf(board) }
    val n = model.n
    CompositionLocalProvider(LocalQueensReducedMotion provides rememberReducedMotion()) {
        Box(
            modifier = modifier
                .aspectRatio(1f)
                .testTag("queens.editor.board")
                .pointerInput(controller, n) {
                    detectTapGestures(onTap = { offset ->
                        val col = (offset.x / size.width * n).toInt().coerceIn(0, n - 1)
                        val row = (offset.y / size.height * n).toInt().coerceIn(0, n - 1)
                        controller.paint(row * n + col)
                    })
                },
        ) {
            val unpainted = MaterialTheme.colorScheme.surfaceContainerHighest
            BoardGrid(
                rows = n,
                cols = n,
                modifier = Modifier.matchParentSize(),
                cellContent = { row, col ->
                    val idx = row * n + col
                    val region = board.regions[idx]
                    Box(
                        Modifier
                            .fillMaxSize()
                            .testTag("queens.editor.cell.$idx")
                            .semantics { stateDescription = describe(region) },
                    ) {
                        if (region == QueensDraft.UNPAINTED) {
                            Box(Modifier.fillMaxSize().background(unpainted))
                        } else {
                            QueensCell(model = model, idx = idx)
                        }
                    }
                },
            )
            val edgeInk = MaterialTheme.colorScheme.onSurface
            val hintColor = LocalBoardColors.current.boardSelection
            Canvas(Modifier.matchParentSize()) { drawCellEdges(model, hintColor, edgeInk) }
        }
    }
}

private fun describe(region: Int): String =
    if (region == QueensDraft.UNPAINTED) UNPAINTED_DESCRIPTION else QueensRegionPalette.hueName(region)

/** A draft has no queens, marks or hints: only the regions reach the play board's model. */
private fun modelOf(board: QueensDraft): QueensBoardModel = QueensBoardModel(
    n = board.size,
    regions = board.regions,
    visuals = List(board.size * board.size) { QueensCellVisual.EMPTY },
    queens = emptyList(),
    violations = emptySet(),
    hintRegion = null,
    hintCells = emptySet(),
    hintCell = null,
    win = false,
)

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 420, showBackground = true)
@Composable
private fun QueensEditorBoardPreview() {
    val blank = EditorDraft.fresh(QueensEditor, QueensVariants.variant(4).id)
    val painted = blank.state.draft.copy(regions = List(16) { if (it % 4 == 3) QueensDraft.UNPAINTED else it / 4 })
    PuzzleBoxTheme {
        QueensEditorBoard(
            controller = QueensEditorController(EditorDraft(QueensEditor, blank.state.copy(draft = painted))) {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
