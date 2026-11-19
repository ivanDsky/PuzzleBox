package com.ivandsky.puzzlebox.games.zipui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.zipui.ZipBoardSpec
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay

/**
 * What the editor board flags: [clash] the cells to blame for a board without a solution, [diff]
 * the cells two paths disagree on, [witness] the solution the board draws as its pipe, in
 * visiting order (empty: the author's own path).
 */
internal data class ZipDiagnostics(
    val clash: Set<Int>,
    val diff: Set<Int>,
    val witness: List<Int>,
)

/**
 * Diagnostics for a [verdict]. The card's [overlay] picks the witness of [held], the last
 * settled verdict, so the path stays while a new analysis runs. It belongs to the board only
 * while [drawn], the path on the board, is the path that analysis solved (or there is none), so
 * an edit hands the pipe back to the author at once.
 */
internal fun zipDiagnostics(
    verdict: Verdict,
    held: Verdict?,
    overlay: EditorOverlay,
    drawn: List<Int>,
): ZipDiagnostics {
    val shown = (held as? Verdict.Multiple)?.takeIf {
        overlay.showOnBoard && (drawn.isEmpty() || drawn == it.solutionA.values)
    }
    val answer = shown?.let {
        if (overlay.witness == EditorOverlay.Witness.B) it.solutionB else it.solutionA
    }
    return ZipDiagnostics(
        clash = (verdict as? Verdict.None)?.conflicts.orEmpty(),
        diff = (verdict as? Verdict.Multiple)?.differingCells.orEmpty(),
        witness = answer?.values.orEmpty(),
    )
}

/**
 * The tint under the play board's lines and pipe on an [n]×[n] grid, one shape over every cell
 * the two paths disagree on, so touching cells merge and no edge doubles. Per diff cell an empty
 * `zip.editor.cell.<idx>.diff` marker.
 */
@Composable
internal fun BoxScope.ZipDiffTint(diff: Set<Int>, n: Int) {
    if (diff.isEmpty()) return
    val tint = LocalBoardColors.current.editorDiffRing.copy(alpha = DIFF_TINT_ALPHA)
    BoxWithConstraints(Modifier.matchParentSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val pitch = size.width / n
            val region = Path()
            diff.forEach { idx ->
                region.addRect(Rect(Offset(idx % n * pitch, idx / n * pitch), Size(pitch, pitch)))
            }
            // The board's outline rounds its corners; a tinted corner cell must not poke out of it.
            val outline = Path().apply {
                addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(pitch * ZipBoardSpec.boardOutlineRadiusFraction)))
            }
            clipPath(outline) { drawPath(region, tint) }
        }
        diff.forEach { idx -> Box(Modifier.cell(idx, n, maxWidth / n).testTag("zip.editor.cell.$idx.diff")) }
    }
}

/**
 * The marks over the play board on an [n]×[n] grid: per cell `zip.editor.cell.<idx>.clash` (the
 * error fill) and `.witness` (an empty marker on a cell the drawn witness visits; the pipe
 * itself is the board's own).
 */
@Composable
internal fun BoxScope.ZipDiagnosticsLayer(diagnostics: ZipDiagnostics, n: Int) {
    val colors = LocalBoardColors.current
    BoxWithConstraints(Modifier.matchParentSize()) {
        val pitch = maxWidth / n
        diagnostics.clash.forEach { idx ->
            Canvas(Modifier.cell(idx, n, pitch).testTag("zip.editor.cell.$idx.clash")) {
                drawRect(colors.editorClashFill)
            }
        }
        diagnostics.witness.forEach { idx ->
            Box(Modifier.cell(idx, n, pitch).testTag("zip.editor.cell.$idx.witness"))
        }
    }
}

private fun Modifier.cell(idx: Int, n: Int, pitch: Dp): Modifier =
    offset(pitch * (idx % n), pitch * (idx / n)).size(pitch)

private const val DIFF_TINT_ALPHA = 0.28f
