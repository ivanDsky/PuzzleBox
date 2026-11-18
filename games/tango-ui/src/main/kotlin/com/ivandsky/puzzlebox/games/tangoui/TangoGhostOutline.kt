package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The dashed accent outline on an open guided square (#1186): the shape cue that tells an invitation from a placed
 * piece. Inset from the cell edge so it never merges with the gridline.
 */
internal fun DrawScope.drawGhostOutline(accent: Color, strokeWidthPx: Float) {
    val pitchPx = size.minDimension
    val inset = pitchPx * TangoBoardSpec.ghostOutlineInsetFraction
    drawRect(
        color = accent.copy(alpha = TangoBoardSpec.ghostOutlineAlpha),
        topLeft = Offset(inset, inset),
        size = Size(size.width - 2 * inset, size.height - 2 * inset),
        style = Stroke(
            width = strokeWidthPx,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(
                    pitchPx * TangoBoardSpec.ghostOutlineDashFraction,
                    pitchPx * TangoBoardSpec.ghostOutlineDashFraction,
                ),
            ),
        ),
    )
}
