package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth

/**
 * A column whose last child is at least as tall as what the others leave of the column's own least height. Inside a
 * `fillMaxSize().verticalScroll(…)` that least height is the viewport, so the last child (the hub's swipe area) reaches
 * the bottom of the screen however little it holds, and still grows past it (and scrolls) when it holds more. A plain
 * `Column` weight can not do that: a scrolling column has no height to share out.
 */
@Composable
internal fun FillRemainingColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val above = measurables.dropLast(1).map { it.measure(loose) }
        val used = above.sumOf { it.height }
        val last = measurables.lastOrNull()?.measure(loose.copy(minHeight = (constraints.minHeight - used).coerceAtLeast(0)))
        val all = above + listOfNotNull(last)
        layout(constraints.constrainWidth(all.maxOfOrNull { it.width } ?: 0), constraints.constrainHeight(all.sumOf { it.height })) {
            var y = 0
            all.forEach {
                it.place(0, y)
                y += it.height
            }
        }
    }
}
