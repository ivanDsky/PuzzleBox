package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/**
 * Draws one series across the full width. Bars grow from [axisY] up, or down when [up] is false, to at most
 * [room]; an empty slice keeps a hairline so the timeline never has a hole. Slices up to [fraction] are [played].
 */
internal fun DrawScope.drawBarSeries(
    counts: IntArray,
    fraction: Float,
    axisY: Float,
    room: Float,
    up: Boolean,
    played: Color,
    unplayed: Color,
) {
    val gap = 2.dp.toPx()
    val bar = (size.width - gap * (ACTIVITY_BUCKETS - 1)) / ACTIVITY_BUCKETS
    val shortest = 12.dp.toPx()
    val peak = counts.max().coerceAtLeast(1)
    for (i in 0 until ACTIVITY_BUCKETS) {
        val height = if (counts[i] == 0) 3.dp.toPx() else shortest + (room - shortest) * counts[i] / peak
        drawRoundRect(
            color = if ((i + 0.5f) / ACTIVITY_BUCKETS <= fraction) played else unplayed,
            topLeft = Offset(i * (bar + gap), if (up) axisY - height else axisY),
            size = Size(bar, height),
            cornerRadius = CornerRadius(bar / 2),
        )
    }
}
