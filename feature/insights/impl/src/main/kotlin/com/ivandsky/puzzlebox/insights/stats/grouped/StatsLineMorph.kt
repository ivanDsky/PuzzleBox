package com.ivandsky.puzzlebox.insights.stats.grouped

import com.ivandsky.puzzlebox.designsystem.chart.LineChartPoint

/** The value scale a line is drawn on: the fixed [valueRange], or its own lowest and highest value. */
internal fun lineScale(points: List<LineChartPoint>, valueRange: ClosedFloatingPointRange<Double>?): ClosedFloatingPointRange<Double> =
    valueRange ?: (points.minOf { it.y }..points.maxOf { it.y })

/**
 * The line [progress] (0..1) of the way from [from] to [to], for the Since / game change morph.
 *
 * The line chart takes a plain point list, so it cannot tween between two lines of different length. Both lines are
 * resampled onto one shared x grid instead: the union of both lines' x positions, each normalised to 0..1 across its
 * own span, so the ends meet exactly and a line with fewer points still has a vertex wherever the other one does. At
 * each grid x the y of each line is read off by linear interpolation and taken as a fraction of its own scale
 * ([fromScale], [toScale]); the fractions are blended and put back on [toScale]. The caller draws the result with
 * [toScale] as the chart's value range, so the axis shows the new values throughout and the old shape slides onto
 * them. A flat line (one value) counts as the middle of its scale.
 *
 * At 1 the result is [to] itself, so the morph ends on the exact new data. The grid is normalised, so the returned
 * x values are 0..1 (only the order and spacing matter to the chart), the first and last point carry [to]'s labels and
 * the ones between are unlabelled. The dots a short line draws on its vertices belong to the grid, so they are the
 * grid's while it morphs: the caller fades the dots and labels out over the first half of the morph and the new
 * line's back in over the last (see [labelSwapAlpha]), so no dot pops. Limitation: a [toScale] of one value (a flat
 * line) has no span to blend into, so the morph draws that line flat at once.
 */
internal fun morphLine(
    from: List<LineChartPoint>,
    fromScale: ClosedFloatingPointRange<Double>,
    to: List<LineChartPoint>,
    toScale: ClosedFloatingPointRange<Double>,
    progress: Float,
): List<LineChartPoint> {
    if (progress >= 1f) return to
    val fromSorted = from.sortedBy { it.x }
    val toSorted = to.sortedBy { it.x }
    val fromXs = normalised(fromSorted)
    val toXs = normalised(toSorted)
    val grid = (fromXs + toXs).sorted().fold(mutableListOf<Double>()) { acc, x ->
        if (acc.isEmpty() || x - acc.last() > GridEpsilon) acc += x
        acc
    }
    val toSpan = toScale.endInclusive - toScale.start
    return grid.mapIndexed { index, x ->
        val fromFraction = fractionAt(fromXs, fromSorted, x, fromScale)
        val toFraction = fractionAt(toXs, toSorted, x, toScale)
        val blended = fromFraction + (toFraction - fromFraction) * progress
        LineChartPoint(
            x = x,
            y = toScale.start + blended * toSpan,
            label = when (index) {
                0 -> toSorted.first().label
                grid.lastIndex -> toSorted.last().label
                else -> ""
            },
        )
    }
}

private const val GridEpsilon = 1e-9

private fun normalised(points: List<LineChartPoint>): List<Double> {
    val first = points.first().x
    val span = points.last().x - first
    return points.mapIndexed { index, point ->
        when {
            span == 0.0 -> if (index == 0) 0.0 else 1.0
            index == 0 -> 0.0
            index == points.lastIndex -> 1.0
            else -> (point.x - first) / span
        }
    }
}

/** Where the line stands at [x] (on its normalised [xs]), as a fraction 0..1 of its [scale]. */
private fun fractionAt(
    xs: List<Double>,
    points: List<LineChartPoint>,
    x: Double,
    scale: ClosedFloatingPointRange<Double>,
): Double {
    val upper = xs.indexOfFirst { it >= x - GridEpsilon }.coerceAtLeast(0)
    val y = if (upper == 0 || xs[upper] - x <= GridEpsilon && x - xs[upper] <= GridEpsilon) {
        points[upper].y
    } else {
        val lower = upper - 1
        val along = (x - xs[lower]) / (xs[upper] - xs[lower])
        points[lower].y + (points[upper].y - points[lower].y) * along
    }
    val span = scale.endInclusive - scale.start
    return if (span == 0.0) 0.5 else (y - scale.start) / span
}
