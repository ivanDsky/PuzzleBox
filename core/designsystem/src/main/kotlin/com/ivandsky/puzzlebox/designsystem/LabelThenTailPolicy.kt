package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import kotlin.math.max

/**
 * Lays a tab cell's content out in one row: the label, then (when there is one) the tail with the
 * count and trailing slot. The tail is measured first at its natural width and the label gets what
 * is left, so a squeezed cell shortens the label and never the count. Unlike a weighted Row it
 * reports honest intrinsic widths, which [UnderlineTabStrip] reads to size the cells.
 */
internal object LabelThenTailPolicy : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val tail = measurables.getOrNull(1)?.measure(loose)
        val gap = if (tail == null) 0 else Spacing.xs.roundToPx()
        val tailWidth = tail?.width ?: 0
        val labelRoom = if (constraints.hasBoundedWidth) (constraints.maxWidth - tailWidth - gap).coerceAtLeast(0) else Constraints.Infinity
        val label = measurables[0].measure(loose.copy(maxWidth = labelRoom))
        val height = max(label.height, tail?.height ?: 0)
        return layout(label.width + gap + tailWidth, height) {
            label.place(0, (height - label.height) / 2)
            tail?.place(label.width + gap, (height - tail.height) / 2)
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables[0].maxIntrinsicWidth(height) + tailWidth(measurables, height)

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables[0].minIntrinsicWidth(height) + tailWidth(measurables, height)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        measurables.maxOf { it.maxIntrinsicHeight(width) }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        measurables.maxOf { it.minIntrinsicHeight(width) }

    private fun IntrinsicMeasureScope.tailWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.getOrNull(1)?.let { it.maxIntrinsicWidth(height) + Spacing.xs.roundToPx() } ?: 0
}
