package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.games.queensui.QueensBoardDefaults
import com.ivandsky.puzzlebox.games.queensui.drawQueen

/**
 * Cell [idx]'s ghost crown when [ghost]: the other answer's queen as the play board's dashed
 * crown outline in the teal diff-ring tint, not an error tint, so it never reads as a placed
 * crown. It **fades in** with the diff ring and tags `queens.editor.cell.<idx>.ghost`.
 * It has no pointer input: the board's gesture layer above takes every tap, so a ghost cell
 * crowns, or refuses when crossed, exactly as it would with no ghost drawn.
 */
@Composable
internal fun QueensGhostLayer(idx: Int, ghost: Boolean, reducedMotion: Boolean) {
    val crownPath = remember { Path() }
    if (!ghost) return
    val tint = LocalBoardColors.current.editorDiffRing
    FadingMark("queens.editor.cell.$idx.ghost", reducedMotion) {
        val dash = QueensBoardDefaults.ghostDash.toPx()
        drawQueen(
            tint,
            QueensBoardDefaults.queenHeightFraction,
            alpha = QueensEditorDefaults.ghostOutlineAlpha,
            path = crownPath,
            style = Stroke(
                width = QueensBoardDefaults.ghostStrokeWeight.toPx(),
                join = StrokeJoin.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * GHOST_GAP_RATIO)),
            ),
        )
    }
}

/** The dash gap as a share of the dash, as the play board's crown ghost. */
private const val GHOST_GAP_RATIO = 0.6f
