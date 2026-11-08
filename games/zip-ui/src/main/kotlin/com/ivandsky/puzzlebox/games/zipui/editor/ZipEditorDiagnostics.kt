package com.ivandsky.puzzlebox.games.zipui.editor

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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay

/**
 * What the editor board flags: [clash] the cells to blame for a board without a solution,
 * [diff] the cells two paths disagree on, [witness] the chosen path, in visiting order.
 */
internal data class ZipDiagnostics(
    val clash: Set<Int>,
    val diff: Set<Int>,
    val witness: List<Int>,
)

/**
 * Diagnostics for a [verdict]. The card's [overlay] draws the witness of [held], the last
 * settled verdict, so the path stays while a new analysis runs.
 */
internal fun zipDiagnostics(verdict: Verdict, held: Verdict?, overlay: EditorOverlay): ZipDiagnostics {
    val shown = (held as? Verdict.Multiple)?.takeIf { overlay.showOnBoard }
    val answer = shown?.let { if (overlay.witness == EditorOverlay.Witness.B) it.solutionB else it.solutionA }
    return ZipDiagnostics(
        clash = (verdict as? Verdict.None)?.conflicts.orEmpty(),
        diff = (verdict as? Verdict.Multiple)?.differingCells.orEmpty(),
        witness = answer?.values.orEmpty(),
    )
}

/**
 * The marks over the play board on an [n]×[n] grid: per cell `zip.editor.cell.<idx>.clash` (the
 * error fill), `.diff` (an inset ring) and `.witness` (a cell the witness path visits, which
 * the layer joins with one line).
 */
@Composable
internal fun BoxScope.ZipDiagnosticsLayer(diagnostics: ZipDiagnostics, n: Int) {
    val colors = LocalBoardColors.current
    val line = MaterialTheme.colorScheme.primary
    BoxWithConstraints(Modifier.matchParentSize()) {
        val pitch = maxWidth / n
        fun Modifier.cell(idx: Int) = offset(pitch * (idx % n), pitch * (idx / n)).size(pitch)
        diagnostics.clash.forEach { idx ->
            Canvas(Modifier.cell(idx).testTag("zip.editor.cell.$idx.clash")) { drawRect(colors.editorClashFill) }
        }
        diagnostics.diff.forEach { idx ->
            Canvas(Modifier.cell(idx).testTag("zip.editor.cell.$idx.diff")) {
                val weight = size.minDimension * DIFF_RING_FRACTION
                drawRect(
                    color = colors.editorDiffRing,
                    topLeft = Offset(weight / 2f, weight / 2f),
                    size = Size(size.width - weight, size.height - weight),
                    style = Stroke(weight),
                )
            }
        }
        if (diagnostics.witness.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                val step = size.width / n
                val path = Path()
                diagnostics.witness.forEachIndexed { i, idx ->
                    val at = Offset((idx % n + 0.5f) * step, (idx / n + 0.5f) * step)
                    if (i == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y)
                }
                drawPath(
                    path,
                    line.copy(alpha = WITNESS_ALPHA),
                    style = Stroke(step * WITNESS_WIDTH_FRACTION, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            diagnostics.witness.forEach { idx ->
                Box(Modifier.cell(idx).testTag("zip.editor.cell.$idx.witness"))
            }
        }
    }
}

private const val DIFF_RING_FRACTION = 0.08f
private const val WITNESS_WIDTH_FRACTION = 0.2f
private const val WITNESS_ALPHA = 0.6f
