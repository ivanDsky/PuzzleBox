package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.game.replay.MoveKind

/** How faint the slices past the playhead are, in every replay seek bar. */
internal const val UNPLAYED_ALPHA = 0.28f

/**
 * The colour of each [MoveKind] in the seek bars, indexed by ordinal: Normal [normal] (primary unless a compare side
 * brings its own tint), Undo outline, Hint secondary, Mistake error. Hint is secondary because neither side tint
 * (primary, tertiary) may be taken for it; [ReplayHintBadge] wears the same role.
 */
internal fun kindColors(colors: ColorScheme, normal: Color = colors.primary): Array<Color> =
    arrayOf(normal, colors.outline, colors.secondary, colors.error)

/** [kindColors] faded for the slices past the playhead. */
internal fun unplayedKindColors(colors: ColorScheme, normal: Color = colors.primary): Array<Color> =
    kindColors(colors, normal).map { it.copy(alpha = UNPLAYED_ALPHA) }.toTypedArray()

/**
 * Draws one series across the full width. Bars grow from [axisY] up, or down when [up] is false, to at most
 * [room]; an empty slice keeps a hairline so the timeline never has a hole. A bar is its kinds stacked from the
 * axis out, Normal first. Slices up to [fraction] are [played], the rest [unplayed]; both are indexed by [MoveKind] ordinal.
 */
internal fun DrawScope.drawBarSeries(
    counts: Array<IntArray>,
    fraction: Float,
    axisY: Float,
    room: Float,
    up: Boolean,
    played: Array<Color>,
    unplayed: Array<Color>,
) {
    val gap = 2.dp.toPx()
    val bar = (size.width - gap * (ACTIVITY_BUCKETS - 1)) / ACTIVITY_BUCKETS
    val shortest = 12.dp.toPx()
    val totals = IntArray(ACTIVITY_BUCKETS) { slice -> counts.sumOf { it[slice] } }
    val peak = totals.max().coerceAtLeast(1)
    for (i in 0 until ACTIVITY_BUCKETS) {
        val height = if (totals[i] == 0) 3.dp.toPx() else shortest + (room - shortest) * totals[i] / peak
        val left = i * (bar + gap)
        val top = if (up) axisY - height else axisY
        val palette = if ((i + 0.5f) / ACTIVITY_BUCKETS <= fraction) played else unplayed
        if (totals[i] == 0) {
            drawRoundRect(palette[MoveKind.Normal.ordinal], Offset(left, top), Size(bar, height), CornerRadius(bar / 2))
            continue
        }
        val rounded = Path().apply { addRoundRect(RoundRect(left, top, left + bar, top + height, CornerRadius(bar / 2))) }
        clipPath(rounded) {
            var stacked = 0f
            for (kind in MoveKind.entries) {
                val count = counts[kind.ordinal][i]
                if (count == 0) continue
                val part = height * count / totals[i]
                drawRect(palette[kind.ordinal], Offset(left, if (up) axisY - stacked - part else axisY + stacked), Size(bar, part))
                stacked += part
            }
        }
    }
}
