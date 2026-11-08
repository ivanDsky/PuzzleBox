package com.ivandsky.puzzlebox.games.sudokuui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuDraft
import com.ivandsky.puzzlebox.games.sudoku.engine.rules.SudokuConflicts
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import com.ivandsky.puzzlebox.games.sudokuui.gridStrokes

/**
 * What the editor board flags: [clash] the givens that break a rule, with the cells a None
 * verdict names, [diff] the cells two
 * answers disagree on, [witness] the chosen answer's digits for the cells still empty.
 */
internal data class SudokuDiagnostics(
    val clash: Set<Int>,
    val diff: Set<Int>,
    val witness: Map<Int, Int>,
)

/**
 * Diagnostics for [board] under [verdict]. The card's [overlay] draws the witness of [held],
 * the last settled verdict, so the digits stay while a new analysis runs.
 */
internal fun sudokuDiagnostics(
    board: SudokuDraft,
    geometry: SudokuGeometry,
    verdict: Verdict,
    held: Verdict?,
    overlay: EditorOverlay,
): SudokuDiagnostics {
    val shown = (held as? Verdict.Multiple)?.takeIf { overlay.showOnBoard }
    val answer = shown?.let { if (overlay.witness == EditorOverlay.Witness.B) it.solutionB else it.solutionA }
    return SudokuDiagnostics(
        clash = givenConflicts(board, geometry) + (verdict as? Verdict.None)?.conflicts.orEmpty(),
        diff = (verdict as? Verdict.Multiple)?.differingCells.orEmpty(),
        witness = answer?.values.orEmpty()
            .withIndex()
            .filter { (idx, _) -> board.givens.getOrElse(idx) { 0 } == 0 }
            .associate { (idx, digit) -> idx to digit },
    )
}

/** The cells whose givens share a digit with a peer. */
private fun givenConflicts(board: SudokuDraft, geometry: SudokuGeometry): Set<Int> {
    val cells = board.givens.map { if (it == 0) SudokuCell.Empty else SudokuCell.Given(it) }
    return SudokuConflicts.cellsIn(SudokuConflicts.find(SudokuBoard(geometry, cells)))
}

/**
 * The marks over the grid: per cell `sudoku.editor.cell.<idx>.clash` (an inset error ring),
 * `.diff` (the diff-ring tint) and `.witness` (the faint digit of the chosen answer).
 */
@Composable
internal fun SudokuDiagnosticsLayer(size: Int, diagnostics: SudokuDiagnostics, modifier: Modifier = Modifier) {
    val frame = gridStrokes(size).box
    val diffTint = LocalBoardColors.current.editorDiffRing.copy(alpha = DIFF_ALPHA)
    val error = MaterialTheme.colorScheme.error
    val faint = MaterialTheme.colorScheme.onSurface.copy(alpha = WITNESS_ALPHA)
    Column(modifier.padding(frame)) {
        for (row in 0 until size) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (col in 0 until size) {
                    val idx = row * size + col
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (idx in diagnostics.diff) {
                            Box(Modifier.matchParentSize().testTag("sudoku.editor.cell.$idx.diff").background(diffTint))
                        }
                        if (idx in diagnostics.clash) {
                            Box(
                                Modifier.matchParentSize().padding(CLASH_INSET)
                                    .testTag("sudoku.editor.cell.$idx.clash")
                                    .border(CLASH_RING, error),
                            )
                        }
                        diagnostics.witness[idx]?.let { digit ->
                            val fontSize = with(LocalDensity.current) { (min(maxWidth, maxHeight) * DIGIT_FRACTION).toSp() }
                            Text(
                                text = digit.toString(),
                                color = faint,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = fontSize,
                                    lineHeight = fontSize,
                                    textAlign = TextAlign.Center,
                                ),
                                modifier = Modifier.testTag("sudoku.editor.cell.$idx.witness"),
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val DIFF_ALPHA = 0.35f
private const val WITNESS_ALPHA = 0.4f
private const val DIGIT_FRACTION = 0.6f
private val CLASH_INSET = 1.dp
private val CLASH_RING = 2.dp
