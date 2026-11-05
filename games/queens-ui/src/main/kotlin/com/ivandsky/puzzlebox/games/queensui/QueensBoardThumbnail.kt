package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.BoardTokens
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.gameui.BoardGrid
import com.ivandsky.puzzlebox.gameui.BoardPreview
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensState
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.codec.QueensSessionCodec
import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSolver
import com.ivandsky.puzzlebox.games.queens.queensPlugin
import com.ivandsky.puzzlebox.games.queens.render.QueensRendererModel

/**
 * The hub's read-only board for [preview] ([com.ivandsky.puzzlebox.gameui.GamePlayUi.previewModel]):
 * the saved queens (no hint) for a save, the bare regions for a fresh puzzle, and the
 * answer's queens for a solved one. Marks stay off the thumbnail — the renderer model
 * carries regions and queens only. Null for anything Queens cannot read; never throws.
 */
internal fun queensPreviewModel(preview: BoardPreview): QueensRendererModel? = runCatching {
    when (preview) {
        is BoardPreview.InProgress -> {
            val envelope = SessionBlob.decode(preview.bytes)?.takeIf { it.game == QueensDefinition.id }
            val session = (envelope?.let { QueensSessionCodec.deserialize(it.session) } as? DecodeResult.Ok)?.value
            session?.let { queensPlugin.rendererModel(queensPlugin.stateOf(it), null, null) }
        }
        is BoardPreview.Fresh -> queensPuzzleOf(preview.puzzle)?.let { modelOf(it, emptyList()) }
        is BoardPreview.Solved -> queensPuzzleOf(preview.puzzle)?.let { puzzle ->
            (puzzle.solution ?: QueensSolver().solve(puzzle))?.let { modelOf(puzzle, it) }
        }
    }
}.getOrNull()

private fun queensPuzzleOf(puzzle: Puzzle): QueensPuzzle? {
    if (puzzle.gameId != QueensDefinition.id) return null
    return (QueensCodec.decode(puzzle.payload) as? DecodeResult.Ok)?.value
}

private fun modelOf(puzzle: QueensPuzzle, queens: List<Cell>): QueensRendererModel =
    queensPlugin.rendererModel(QueensState(puzzle, queens.toSet()), null, null)

/**
 * [model] drawn with the play board's own layers (region fills, crowns, cell and region
 * edges) and nothing else: no input, no motion, no hint, no conflict stripes. Every
 * stroke is a hairline-scale dp and the crown is a cell fraction, so it reads at
 * thumbnail size. Unlike the play board, the thumbnail closes the board with a
 * Tango-weight outer border and sits [QueensBoardDefaults.thumbnailInsetFraction] inside
 * [modifier]'s square, so the hub's three thumbnails share one framing.
 */
@Composable
internal fun QueensBoardThumbnail(model: QueensRendererModel, modifier: Modifier = Modifier) {
    val board = QueensBoardModel(
        n = model.n,
        regions = model.regions,
        visuals = List(model.n * model.n) { if (it in model.queens) QueensCellVisual.QUEEN else QueensCellVisual.EMPTY },
        queens = model.queens.sorted(),
        violations = emptySet(),
        hintRegion = null,
        hintCells = emptySet(),
        hintCell = null,
        win = model.status == QueensRendererModel.Status.COMPLETE,
    )
    val boardTokens = LocalBoardColors.current
    val edgeInk = MaterialTheme.colorScheme.onSurface
    // Cells snap to their visual on first composition; reduced motion pins any later change.
    CompositionLocalProvider(LocalQueensReducedMotion provides true) {
        BoxWithConstraints(modifier.aspectRatio(1f).testTag(THUMBNAIL_TAG)) {
            val inset = minOf(maxWidth, maxHeight) * QueensBoardDefaults.thumbnailInsetFraction
            Box(
                Modifier
                    .matchParentSize()
                    .padding(inset)
                    .border(BoardTokens.boardGridLineWeight * 2, boardTokens.boardGridLine),
            ) {
                BoardGrid(
                    rows = board.n,
                    cols = board.n,
                    modifier = Modifier.matchParentSize(),
                    cellContent = { row, col -> QueensCell(model = board, idx = row * board.n + col) },
                )
                Canvas(Modifier.matchParentSize()) {
                    drawCellEdges(board, boardTokens.boardSelection, edgeInk)
                }
            }
        }
    }
}

/** Test tag of the thumbnail board. */
internal const val THUMBNAIL_TAG = "queens.thumbnail"
