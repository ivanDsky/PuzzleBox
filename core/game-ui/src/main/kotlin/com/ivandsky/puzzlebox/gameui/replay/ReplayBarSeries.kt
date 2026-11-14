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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Density
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

/** One bar of a series, laid out: its box, the clip it is drawn through and each kind's stacked part (indexed by [MoveKind] ordinal). */
internal class BarShape(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    /** Null for an empty slice, which is a plain hairline with no kinds to stack. */
    val clip: Path?,
    val partTops: FloatArray,
    val partHeights: FloatArray,
)

/** A series laid out once for a size: the draw phase only picks colours and draws. */
internal class BarSeriesShapes(val bars: Array<BarShape>)

/**
 * Lays one series out across [width] starting [inset] from the left edge (the needle's track, so fraction 0 plays nothing and 1 plays every bar). Bars grow from [axisY] up, or down when [up] is false, to at most
 * [room]; an empty slice keeps a hairline so the timeline never has a hole. A bar is its kinds stacked from the
 * axis out, Normal first. Build it where the size is known and cache it, so the draw phase allocates nothing.
 */
internal fun Density.barSeriesShapes(
    counts: Array<IntArray>,
    width: Float,
    axisY: Float,
    room: Float,
    up: Boolean,
    inset: Float = 0f,
): BarSeriesShapes {
    val gap = 2.dp.toPx()
    val bar = (width - gap * (ACTIVITY_BUCKETS - 1)) / ACTIVITY_BUCKETS
    val shortest = 12.dp.toPx()
    val totals = IntArray(ACTIVITY_BUCKETS) { slice -> counts.sumOf { it[slice] } }
    val peak = totals.max().coerceAtLeast(1)
    return BarSeriesShapes(
        Array(ACTIVITY_BUCKETS) { i ->
            val height = if (totals[i] == 0) 3.dp.toPx() else shortest + (room - shortest) * totals[i] / peak
            val left = inset + i * (bar + gap)
            val top = if (up) axisY - height else axisY
            val partTops = FloatArray(counts.size)
            val partHeights = FloatArray(counts.size)
            if (totals[i] != 0) {
                var stacked = 0f
                for (kind in MoveKind.entries) {
                    val count = counts[kind.ordinal][i]
                    if (count == 0) continue
                    val part = height * count / totals[i]
                    partTops[kind.ordinal] = if (up) axisY - stacked - part else axisY + stacked
                    partHeights[kind.ordinal] = part
                    stacked += part
                }
            }
            val clip = if (totals[i] == 0) null else Path().apply {
                addRoundRect(RoundRect(left, top, left + bar, top + height, CornerRadius(bar / 2)))
            }
            BarShape(left, top, bar, height, clip, partTops, partHeights)
        },
    )
}

/** How much of a bar at [left] of [width] the needle at [needleX] has passed: all, none, or the part left of the needle. */
internal fun playedExtent(left: Float, width: Float, needleX: Float): Float = (needleX - left).coerceIn(0f, width)

/**
 * Draws a laid-out series. A bar left of the playhead is [played], one right of it [unplayed], and the bar under it is
 * split at [needleX], the x the playhead is drawn at; both palettes are indexed by [MoveKind] ordinal.
 * Allocates nothing: it runs on every frame the playhead moves.
 */
internal fun DrawScope.drawBarSeries(shapes: BarSeriesShapes, needleX: Float, played: Array<Color>, unplayed: Array<Color>) {
    val bars = shapes.bars
    for (i in bars.indices) {
        val shape = bars[i]
        val extent = playedExtent(shape.left, shape.width, needleX)
        val clip = shape.clip
        if (extent <= 0f || extent >= shape.width) {
            drawBar(shape, clip, if (extent > 0f) played else unplayed)
        } else {
            val split = shape.left + extent
            clipRect(left = shape.left, top = 0f, right = split, bottom = size.height) { drawBar(shape, clip, played) }
            clipRect(left = split, top = 0f, right = shape.left + shape.width, bottom = size.height) { drawBar(shape, clip, unplayed) }
        }
    }
}

private fun DrawScope.drawBar(shape: BarShape, clip: Path?, palette: Array<Color>) {
    if (clip == null) {
        drawRoundRect(palette[MoveKind.Normal.ordinal], Offset(shape.left, shape.top), Size(shape.width, shape.height), CornerRadius(shape.width / 2))
        return
    }
    clipPath(clip) {
        for (kind in shape.partHeights.indices) {
            val part = shape.partHeights[kind]
            if (part == 0f) continue
            drawRect(palette[kind], Offset(shape.left, shape.partTops[kind]), Size(shape.width, part))
        }
    }
}
