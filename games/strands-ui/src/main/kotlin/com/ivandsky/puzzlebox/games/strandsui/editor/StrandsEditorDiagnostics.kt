package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strandsui.board.strandsCellSizeFor
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay

/**
 * What the editor board flags: [clash] the cells no word can cover (a None verdict), [diff] the
 * cells two tilings give a different word, [witness] the chosen tiling's word index per cell.
 */
internal data class StrandsDiagnostics(
    val clash: Set<Int>,
    val diff: Set<Int>,
    val witness: Map<Int, Int>,
)

/**
 * Diagnostics under [verdict]. The card's [overlay] draws the tiling of [held], the last settled
 * verdict, so the paths stay while a new analysis runs.
 */
internal fun strandsDiagnostics(verdict: Verdict, held: Verdict?, overlay: EditorOverlay): StrandsDiagnostics {
    val shown = (held as? Verdict.Multiple)?.takeIf { overlay.showOnBoard }
    val tiling = shown?.let { if (overlay.witness == EditorOverlay.Witness.B) it.solutionB else it.solutionA }
    return StrandsDiagnostics(
        clash = (verdict as? Verdict.None)?.conflicts.orEmpty(),
        diff = (verdict as? Verdict.Multiple)?.differingCells.orEmpty(),
        witness = tiling?.values.orEmpty().withIndex().associate { (cell, word) -> cell to word },
    )
}

/**
 * The marks over the grid, per cell `strands.editor.cell.<idx>.clash` (an inset error ring),
 * `.diff` (the diff-ring outline) and `.witness` (a tint per word of the chosen tiling, its word
 * index as the state, a line on each side facing another word).
 */
@Composable
internal fun StrandsDiagnosticsLayer(diagnostics: StrandsDiagnostics, modifier: Modifier = Modifier) {
    val ring = LocalBoardColors.current.editorDiffRing
    val error = MaterialTheme.colorScheme.error
    val scheme = MaterialTheme.colorScheme
    val tints = listOf(scheme.primary, scheme.tertiary, scheme.secondary)
    BoxWithConstraints(modifier) {
        val cell = strandsCellSizeFor(maxWidth, maxHeight, StrandsDraft.ROWS, StrandsDraft.COLS)
        Box(Modifier.fillMaxWidth().height(cell * StrandsDraft.ROWS), contentAlignment = Alignment.Center) {
            Column(Modifier.requiredSize(cell * StrandsDraft.COLS, cell * StrandsDraft.ROWS)) {
                repeat(StrandsDraft.ROWS) { row ->
                    Row {
                        repeat(StrandsDraft.COLS) { col ->
                            val idx = row * StrandsDraft.COLS + col
                            Box(Modifier.size(cell)) {
                                if (idx in diagnostics.diff) {
                                    Box(
                                        Modifier.matchParentSize().padding(DIFF_INSET)
                                            .testTag("strands.editor.cell.$idx.diff")
                                            .border(RING_WIDTH, ring),
                                    )
                                }
                                if (idx in diagnostics.clash) {
                                    Box(
                                        Modifier.matchParentSize().padding(CLASH_INSET)
                                            .testTag("strands.editor.cell.$idx.clash")
                                            .border(RING_WIDTH, error),
                                    )
                                }
                                diagnostics.witness[idx]?.let { word ->
                                    val tint = tints[word % tints.size]
                                    Box(
                                        Modifier.matchParentSize()
                                            .background(tint.copy(alpha = WITNESS_ALPHA))
                                            .drawBehind { drawWordEdges(idx, word, diagnostics.witness, tint) }
                                            .semantics { stateDescription = word.toString() }
                                            .testTag("strands.editor.cell.$idx.witness"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A line on each side of cell [idx] whose orthogonal neighbour holds another word (or none). */
private fun DrawScope.drawWordEdges(idx: Int, word: Int, witness: Map<Int, Int>, color: Color) {
    val row = idx / StrandsDraft.COLS
    val col = idx % StrandsDraft.COLS
    val width = EDGE_WIDTH.toPx()
    fun differs(r: Int, c: Int) =
        r in 0 until StrandsDraft.ROWS && c in 0 until StrandsDraft.COLS &&
            witness[r * StrandsDraft.COLS + c] != word
    if (differs(row - 1, col)) drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), width)
    if (differs(row + 1, col)) drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), width)
    if (differs(row, col - 1)) drawLine(color, Offset(0f, 0f), Offset(0f, size.height), width)
    if (differs(row, col + 1)) drawLine(color, Offset(size.width, 0f), Offset(size.width, size.height), width)
}

private const val WITNESS_ALPHA = 0.3f
private val DIFF_INSET = 1.dp
private val CLASH_INSET = 4.dp
private val RING_WIDTH = 2.dp
private val EDGE_WIDTH = 2.dp
