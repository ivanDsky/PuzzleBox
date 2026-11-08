package com.ivandsky.puzzlebox.games.sudokuui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuDraft
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuEditor
import com.ivandsky.puzzlebox.games.sudokuui.SudokuBoardModel
import com.ivandsky.puzzlebox.games.sudokuui.SudokuCellModel
import com.ivandsky.puzzlebox.games.sudokuui.SudokuGrid
import com.ivandsky.puzzlebox.games.sudokuui.SudokuKeypadActions
import com.ivandsky.puzzlebox.games.sudokuui.SudokuSizing
import com.ivandsky.puzzlebox.games.sudokuui.gridStrokes
import com.ivandsky.puzzlebox.gameui.PlayBoardMaxWidth

/**
 * The editor's board: the play [SudokuGrid] over the [controller]'s draft, every placed digit
 * drawn as a given. A tap on a cell selects it; below it sit the counted digit keys and the
 * tools row (Erase, Clear, Symmetry). Empty cells whose mirror holds a given are outlined
 * dashed; empty peers where the selected digit would clash are hatched. The
 * [verdict] and the card's witness choice ([SudokuEditorController.overlay]) are drawn by
 * [SudokuDiagnosticsLayer]. Sized by [SudokuSizing] for the space [modifier] gives it.
 */
@Composable
internal fun SudokuEditorBoard(
    controller: SudokuEditorController,
    geometry: SudokuGeometry,
    modifier: Modifier = Modifier,
    verdict: Verdict = Verdict.Checking,
) {
    val draft by controller.draft.collectAsState()
    val selected by controller.selected.collectAsState()
    val board = draft.state.draft as SudokuDraft
    val symmetry by controller.symmetry.collectAsState()
    val overlay by controller.overlay.collectAsState()
    // The last settled verdict, so the witness digits hold while a new analysis runs.
    val settled = remember { mutableStateOf<Verdict?>(null) }
    SideEffect { if (verdict != Verdict.Checking) settled.value = verdict }
    val held = if (verdict == Verdict.Checking) settled.value else verdict
    val diagnostics = sudokuDiagnostics(board, geometry, verdict, held, overlay)
    val model = remember(board, selected, diagnostics.clash) { editorBoardModel(board, geometry, selected, diagnostics.clash) }
    val unpaired = remember(board, symmetry) { symmetry.unpaired(board) }
    val hatched = remember(board, selected) { wouldClash(board, geometry, selected) }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val roomy = SudokuSizing.layout(maxWidth, maxHeight, board.size, boardMax = PlayBoardMaxWidth)
        // A cramped pane budgets no action row; the tools row takes a minimum-height one.
        val layout = if (roomy.actions == SudokuKeypadActions.Row) {
            roomy
        } else {
            val reserved = SudokuSizing.keyMin + roomy.keyGap
            SudokuSizing.layout(maxWidth, (maxHeight - reserved).coerceAtLeast(0.dp), board.size, boardMax = PlayBoardMaxWidth)
        }
        val toolHeight = if (layout.actions == SudokuKeypadActions.Row) layout.actionKey else SudokuSizing.keyMin
        Column(
            modifier = Modifier.testTag("sudoku.editor.board"),
            verticalArrangement = Arrangement.spacedBy(layout.keyGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(layout.board)) {
                SudokuGrid(model, controller::tap, Modifier.fillMaxSize())
                SudokuEditorMarks(board.size, unpaired, hatched, Modifier.fillMaxSize())
                SudokuDiagnosticsLayer(board.size, diagnostics, Modifier.fillMaxSize())
            }
            SudokuEditorKeypad(controller, board, layout)
            SudokuEditorTools(controller, board.givens.any { it != 0 }, unpaired.size, toolHeight)
        }
    }
}

/** The empty peers of a selected digit's cell: placing that digit on any of them would clash. */
private fun wouldClash(draft: SudokuDraft, geometry: SudokuGeometry, selected: Int?): Set<Int> {
    if (selected == null || draft.givens[selected] == 0) return emptySet()
    return geometry.peersOf(selected).filterTo(mutableSetOf()) { draft.givens[it] == 0 }
}

/**
 * Peers and same-digit cells of the selection are tinted like play's; a digit clashing with a
 * peer is flagged.
 */
private fun editorBoardModel(
    draft: SudokuDraft,
    geometry: SudokuGeometry,
    selected: Int?,
    conflicts: Set<Int>,
): SudokuBoardModel {
    val peers = selected?.let { geometry.peersOf(it).toSet() }.orEmpty()
    val selectedDigit = selected?.let { draft.givens[it] } ?: 0
    val models = draft.givens.mapIndexed { idx, digit ->
        SudokuCellModel(
            idx = idx,
            digit = digit,
            notes = 0,
            given = digit != 0,
            selected = idx == selected,
            peer = idx in peers,
            sameDigit = selectedDigit != 0 && idx != selected && digit == selectedDigit,
            conflict = idx in conflicts,
            hintTarget = false,
            hintUnit = false,
            hintStruck = false,
        )
    }
    return SudokuBoardModel(geometry.size, geometry.boxRows, geometry.boxCols, models)
}

/**
 * Dashed outlines on the [outlined] cells and 22% stripes on the [hatched] ones, laid over the
 * grid inside its frame. Taps pass through to the grid.
 */
@Composable
private fun SudokuEditorMarks(size: Int, outlined: Set<Int>, hatched: Set<Int>, modifier: Modifier = Modifier) {
    val frame = gridStrokes(size).box
    val outline = MaterialTheme.colorScheme.onTertiaryContainer
    val stripe = LocalBoardColors.current.editorSplitHatch.copy(alpha = HATCH_ALPHA)
    Column(modifier.padding(frame)) {
        for (row in 0 until size) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (col in 0 until size) {
                    val idx = row * size + col
                    val cell = Modifier.weight(1f).fillMaxHeight()
                    when {
                        idx in hatched -> Box(cell.testTag("sudoku.editor.hatch").drawBehind { drawStripes(stripe) })
                        idx in outlined -> Box(cell.testTag("sudoku.editor.unpaired.cell").drawBehind { drawDashedOutline(outline) })
                        else -> Box(cell)
                    }
                }
            }
        }
    }
}

private const val HATCH_ALPHA = 0.22f

private fun DrawScope.drawStripes(color: Color) {
    val pitch = size.minDimension / STRIPES_PER_CELL
    clipRect {
        var start = -size.height
        while (start < size.width) {
            drawLine(color, Offset(start, size.height), Offset(start + size.height, 0f), strokeWidth = pitch / 2)
            start += pitch
        }
    }
}

private fun DrawScope.drawDashedOutline(color: Color) {
    val width = OUTLINE_STROKE.toPx()
    val dash = size.minDimension / DASHES_PER_SIDE
    drawRect(
        color,
        topLeft = Offset(width, width),
        size = Size(size.width - 2 * width, size.height - 2 * width),
        style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
    )
}

private const val STRIPES_PER_CELL = 4
private const val DASHES_PER_SIDE = 6
private val OUTLINE_STROKE = 2.dp

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 480, showBackground = true)
@Composable
internal fun SudokuEditorBoardPreview() {
    val editor = SudokuEditor(SudokuGames.MINI)
    val controller = remember {
        SudokuEditorController(EditorDraft.fresh(editor, editor.variants.first())) {}.apply {
            tap(0)
            digit(4)
            tap(1)
        }
    }
    PuzzleBoxTheme {
        SudokuEditorBoard(controller, SudokuGames.MINI.variant.geometry)
    }
}
