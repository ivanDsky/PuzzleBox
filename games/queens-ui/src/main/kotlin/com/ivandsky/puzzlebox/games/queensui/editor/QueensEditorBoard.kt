package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.editor.QueensCrowns
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import com.ivandsky.puzzlebox.games.queensui.LocalQueensReducedMotion
import com.ivandsky.puzzlebox.games.queensui.QueensBoardModel
import com.ivandsky.puzzlebox.games.queensui.QueensBrand
import com.ivandsky.puzzlebox.games.queensui.QueensCell
import com.ivandsky.puzzlebox.games.queensui.QueensCellVisual
import com.ivandsky.puzzlebox.games.queensui.QueensRegionPalette
import com.ivandsky.puzzlebox.games.queensui.drawCellEdges
import com.ivandsky.puzzlebox.gameui.BoardGrid

/** What a cell's semantics say when no region is painted on it yet. */
internal const val UNPAINTED_DESCRIPTION = "unpainted"

/**
 * The editor's square board (spec S3, select then colour; flow C crowns): the play board's
 * region fills and edges over the [controller]'s draft, an unpainted cell left on the surface
 * tone, and the answer crowns with their auto-× ([QueensCrownLayer]) in both modes. Touch is
 * [queensBoardGestures]: a tap toggles a crown (Crown, a refused tap jolts that cell) or a
 * selected cell (Paint); a drag from a coloured cell previews its colour stroke live (crossed
 * cells tagged `.stroke`), and a drag from an unpainted cell selects what it crosses (Paint).
 * The selection wears an accent wash and outline, and while it is non-empty the colour
 * ring sits at the lift point, pushed back inside the board and the window. The context
 * bar below counts the selection; its Area and All of colour chips grow it and re-open the
 * ring at the new centroid. Each cell says its region's hue in its state description, so
 * tests read the painting without pixels. Over the fills, [QueensCellDiagnostics] shows what
 * the [verdict] found and which region pieces stray.
 */
@Composable
internal fun QueensEditorBoard(controller: QueensEditorController, verdict: Verdict, modifier: Modifier = Modifier) {
    val draft by controller.draft.collectAsState()
    val selection by controller.selection.collectAsState()
    val board = draft.state.draft as QueensDraft
    val touch = remember { QueensBoardTouch() }
    val shown = touch.preview(board)
    val stroked = touch.stroked(board)
    val model = remember(shown) { modelOf(shown) }
    val n = model.n
    val reducedMotion = rememberReducedMotion()
    val diagnostics = rememberQueensDiagnostics(verdict, board)
    val autoX = remember(board) { QueensCrowns.crossed(board) }
    val rejection = rememberQueensRejection(controller)
    var frame by remember { mutableStateOf(BoardFrame.Zero) }
    CompositionLocalProvider(LocalQueensReducedMotion provides reducedMotion) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .testTag("queens.editor.board")
                    .onGloballyPositioned { frame = BoardFrame(it.size, it.positionInRoot(), it.findRootCoordinates().size) }
                    .queensBoardGestures(controller, n, touch),
            ) {
                val unpainted = MaterialTheme.colorScheme.surfaceContainerHighest
                BoardGrid(
                    rows = n,
                    cols = n,
                    modifier = Modifier.matchParentSize(),
                    cellContent = { row, col ->
                        val idx = row * n + col
                        val region = shown.regions[idx]
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
                            QueensCrownLayer(idx, region, idx in board.crowns, idx in autoX, rejection, reducedMotion)
                            QueensCellDiagnostics(idx, diagnostics, reducedMotion)
                            if (idx in stroked) Box(Modifier.matchParentSize().testTag("queens.editor.cell.$idx.stroke"))
                        }
                    },
                )
                val edgeInk = MaterialTheme.colorScheme.onSurface
                val hintColor = LocalBoardColors.current.boardSelection
                val accent = QueensBrand.accent.colors().accent
                Canvas(Modifier.matchParentSize()) {
                    drawCellEdges(model, hintColor, edgeInk)
                    drawSelection(selection, n, accent)
                }
                QueensSelectionRing(
                    visible = selection.isNotEmpty() && !touch.dragging,
                    board = board,
                    selection = selection,
                    lift = touch.lift,
                    frame = frame,
                    reducedMotion = reducedMotion,
                    onPick = controller::pick,
                )
            }
            QueensSelectionChips(controller, onGrown = { touch.lift = centroid(it, n, frame.size) })
        }
    }
}

/** The middle of [cells] in board pixels, where the ring re-opens after a chip grows the selection. */
private fun centroid(cells: Set<Int>, n: Int, board: IntSize): Offset = Offset(
    x = (cells.sumOf { it % n }.toFloat() / cells.size + 0.5f) * board.width / n,
    y = (cells.sumOf { it / n }.toFloat() / cells.size + 0.5f) * board.height / n,
)

/** Accent wash on each selected cell, and an accent outline along the selection's outer edge. */
private fun DrawScope.drawSelection(selection: Set<Int>, n: Int, accent: Color) {
    if (selection.isEmpty()) return
    val cw = size.width / n
    val ch = size.height / n
    val stroke = QueensEditorDefaults.selectionStrokeWeight.toPx()
    val inset = stroke / 2
    val wash = accent.copy(alpha = QueensEditorDefaults.selectionOverlayAlpha)
    fun selected(row: Int, col: Int) = row in 0 until n && col in 0 until n && (row * n + col) in selection
    for (idx in selection) {
        val row = idx / n
        val col = idx % n
        val left = col * cw
        val top = row * ch
        drawRect(wash, Offset(left, top), Size(cw, ch))
        if (!selected(row - 1, col)) drawLine(accent, Offset(left, top + inset), Offset(left + cw, top + inset), stroke)
        if (!selected(row + 1, col)) drawLine(accent, Offset(left, top + ch - inset), Offset(left + cw, top + ch - inset), stroke)
        if (!selected(row, col - 1)) drawLine(accent, Offset(left + inset, top), Offset(left + inset, top + ch), stroke)
        if (!selected(row, col + 1)) drawLine(accent, Offset(left + cw - inset, top), Offset(left + cw - inset, top + ch), stroke)
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
    PuzzleBoxTheme { QueensEditorBoard(previewController(), Verdict.Checking, Modifier.fillMaxWidth()) }
}

@PuzzleBoxPreviews
@Preview(name = "Selection with ring", widthDp = 360, heightDp = 420, showBackground = true)
@Composable
private fun QueensEditorBoardSelectedPreview() {
    val controller = previewController().apply { listOf(5, 6, 9).forEach(::tap) }
    PuzzleBoxTheme { QueensEditorBoard(controller, Verdict.Checking, Modifier.fillMaxWidth()) }
}

@PuzzleBoxPreviews
@Preview(name = "2+ solutions", widthDp = 360, heightDp = 420, showBackground = true)
@Composable
private fun QueensEditorBoardMultiplePreview() {
    val verdict = Verdict.Multiple(Solution(listOf(1, 7, 8, 14)), Solution(listOf(2, 4, 11, 13)), setOf(1, 2, 4, 7, 8, 11, 13, 14))
    PuzzleBoxTheme { QueensEditorBoard(previewController(), verdict, Modifier.fillMaxWidth()) }
}

@PuzzleBoxPreviews
@Preview(name = "No solution, clash", widthDp = 360, heightDp = 420, showBackground = true)
@Composable
private fun QueensEditorBoardClashPreview() {
    PuzzleBoxTheme { QueensEditorBoard(previewController(), Verdict.None(setOf(0, 5)), Modifier.fillMaxWidth()) }
}

@PuzzleBoxPreviews
@Preview(name = "Split region", widthDp = 360, heightDp = 420, showBackground = true)
@Composable
private fun QueensEditorBoardSplitPreview() {
    val regions = List(16) { it / 4 }.toMutableList().apply { this[15] = 0 }
    PuzzleBoxTheme { QueensEditorBoard(previewController(regions), Verdict.Checking, Modifier.fillMaxWidth()) }
}

@PuzzleBoxPreviews
@Preview(name = "Crown mode, one crown", widthDp = 360, heightDp = 420, showBackground = true)
@Composable
private fun QueensEditorBoardCrownPreview() {
    val controller = previewController(List(16) { QueensDraft.UNPAINTED }).apply { tap(5) }
    PuzzleBoxTheme { QueensEditorBoard(controller, Verdict.Checking, Modifier.fillMaxWidth()) }
}

private fun previewController(
    regions: List<Int> = List(16) { if (it % 4 == 3) QueensDraft.UNPAINTED else it / 4 },
): QueensEditorController {
    val blank = EditorDraft.fresh(QueensEditor, QueensVariants.variant(4).id)
    return QueensEditorController(EditorDraft(QueensEditor, blank.state.copy(draft = blank.state.draft.copy(regions = regions)))) {}
}
