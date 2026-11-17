package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

private val DividerHeight = 1.dp

/**
 * The strip itself: cells for [tabs], a [DividerHeight] `outlineVariant` divider under them, and
 * the `primaryText` indicator at [position] (a tab index, fractional between two). The position
 * is read where the strip lays out, so a value that changes every frame moves the indicator
 * without recomposing the caller. A tap on a cell calls [onSelect] with its index.
 */
@Composable
internal fun UnderlineTabStrip(
    tabs: List<UnderlineTab>,
    position: () -> Float,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val primaryText = LocalPrimaryText.current
    Column(modifier.fillMaxWidth().testTag("underline.tabs")) {
        Layout(
            content = {
                tabs.forEachIndexed { index, tab ->
                    val at = position()
                    UnderlineTabCell(
                        tab = tab,
                        closeness = (1f - abs(at - index)).coerceIn(0f, 1f),
                        selected = at.roundToInt() == index,
                        onClick = { onSelect(index) },
                    )
                }
                Box(Modifier.background(primaryText).testTag("underline.tabs.indicator"))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { measurables, constraints ->
            val cells = measurables.dropLast(1)
            val indicator = measurables.last()
            val tabHeight = TabHeight.roundToPx()
            val barHeight = TabIndicatorHeight.roundToPx()
            val edges = tabEdges(cells.map { it.maxIntrinsicWidth(tabHeight) }, constraints.maxWidth)
            val placed = cells.mapIndexed { i, cell ->
                cell.measure(Constraints.fixed(edges[i + 1] - edges[i], tabHeight).copy(maxHeight = Constraints.Infinity))
            }
            val height = placed.maxOfOrNull { it.height } ?: tabHeight
            val at = position().coerceIn(0f, (cells.size - 1).coerceAtLeast(0).toFloat())
            val from = floor(at).toInt()
            val to = (from + 1).coerceAtMost(cells.size - 1)
            val share = at - from
            val left = lerpInt(edges[from], edges[to], share)
            val width = lerpInt(edges[from + 1] - edges[from], edges[to + 1] - edges[to], share)
            val bar = indicator.measure(Constraints.fixed(width, barHeight))
            layout(constraints.maxWidth, height) {
                placed.forEachIndexed { i, cell -> cell.place(edges[i], 0) }
                bar.place(left, height - barHeight)
            }
        }
        Box(Modifier.fillMaxWidth().height(DividerHeight).background(scheme.outlineVariant).testTag("underline.tabs.divider"))
    }
}

/**
 * The cell edges (count + 1 values from 0 to [total]) for tabs of the given natural widths: equal
 * shares when every natural width fits one, otherwise each tab takes the strip in proportion to
 * its own. A natural width is only squeezed (the label ellipsizes) once together they pass [total].
 */
private fun tabEdges(natural: List<Int>, total: Int): IntArray {
    val count = natural.size
    val edges = IntArray(count + 1)
    if (count == 0) return edges
    val sum = natural.sum()
    val equal = natural.all { it * count <= total } || sum == 0
    var run = 0f
    for (i in 0 until count) {
        run += if (equal) total.toFloat() / count else total.toFloat() * natural[i] / sum
        edges[i + 1] = run.roundToInt()
    }
    edges[count] = total
    return edges
}

private fun lerpInt(from: Int, to: Int, share: Float): Int = from + ((to - from) * share).roundToInt()
