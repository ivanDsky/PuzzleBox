package com.ivandsky.puzzlebox.games.tangoui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.editor.TangoClashLocator
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraft
import com.ivandsky.puzzlebox.games.tangoui.TangoBoardSpec
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay

/**
 * What the editor board flags: [clash] the cells whose givens break a rule, [diff] the cells
 * two answers disagree on, [witness] the chosen answer's symbols for the cells still empty.
 */
internal data class TangoDiagnostics(
    val clash: Set<Int>,
    val diff: Set<Int>,
    val witness: Map<Int, TangoCell>,
)

/**
 * Diagnostics for [board]. Clashes come from the givens themselves, so they show at once,
 * with the None verdict's own cells; the shade follows [verdict]. The card's [overlay] draws
 * the witness of [held], the last settled verdict, so the symbols stay while a new analysis runs.
 */
internal fun tangoDiagnostics(
    board: TangoDraft,
    verdict: Verdict,
    held: Verdict?,
    overlay: EditorOverlay,
): TangoDiagnostics {
    val puzzle = TangoPuzzle(VariantId("tango/n${TangoDraft.N}"), TangoDraft.N, board.givens, board.markers)
    val shown = (held as? Verdict.Multiple)?.takeIf { overlay.showOnBoard }
    val answer = shown?.let { if (overlay.witness == EditorOverlay.Witness.B) it.solutionB else it.solutionA }
    return TangoDiagnostics(
        clash = TangoClashLocator.conflicts(puzzle) + (verdict as? Verdict.None)?.conflicts.orEmpty(),
        diff = (verdict as? Verdict.Multiple)?.differingCells.orEmpty(),
        witness = answer?.values.orEmpty()
            .withIndex()
            .filter { (idx, _) -> idx !in board.givens }
            .associate { (idx, value) -> idx to TangoCell.entries[value] },
    )
}

/**
 * The marks over the play board: per cell `tango.editor.cell.<idx>.clash` (an inset error
 * ring, so the given stays legible), `.diff` (the shade) and `.witness` (the faint symbol the
 * renderer draws from [TangoDiagnostics.witness]).
 */
@Composable
internal fun BoxScope.TangoDiagnosticsLayer(diagnostics: TangoDiagnostics) {
    val colors = LocalBoardColors.current
    val error = MaterialTheme.colorScheme.error
    BoxWithConstraints(Modifier.matchParentSize()) {
        val pitch = maxWidth / TangoDraft.N
        fun Modifier.cell(idx: Int) =
            offset(pitch * (idx % TangoDraft.N), pitch * (idx / TangoDraft.N)).size(pitch)
        diagnostics.diff.forEach { idx ->
            Canvas(Modifier.cell(idx).testTag("tango.editor.cell.$idx.diff")) {
                drawRect(colors.editorDiffRing.copy(alpha = TangoBoardSpec.editDifferAlpha))
            }
        }
        diagnostics.clash.forEach { idx ->
            Canvas(Modifier.cell(idx).testTag("tango.editor.cell.$idx.clash")) {
                val weight = size.minDimension * TangoBoardSpec.editClashRingFraction
                drawRect(
                    color = error,
                    topLeft = Offset(weight / 2f, weight / 2f),
                    size = Size(size.width - weight, size.height - weight),
                    style = Stroke(weight),
                )
            }
        }
        diagnostics.witness.keys.forEach { idx ->
            Box(Modifier.cell(idx).testTag("tango.editor.cell.$idx.witness").fillMaxSize())
        }
    }
}
