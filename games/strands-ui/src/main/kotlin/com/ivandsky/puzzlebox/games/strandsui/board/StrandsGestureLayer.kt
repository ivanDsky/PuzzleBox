package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strandsui.Trace
import com.ivandsky.puzzlebox.games.strandsui.TraceInput
import com.ivandsky.puzzlebox.games.strandsui.TraceStep
import com.ivandsky.puzzlebox.gameui.GridGeometry
import kotlin.math.ceil

/**
 * Strands' own input (A12), laid over the whole board area with the [boardSize] board
 * centred in it. It only maps positions to cells; every rule is [TraceInput]'s.
 *
 * A touch-down on a cell waits: lifting without reaching another cell's inner circle is a
 * [TraceInput.tap]; reaching one turns the gesture into a drag that starts at the first
 * cell. A down beside the board clears the trace. [onTouch] fires on every down, before any
 * step, so the board can retire running motion: input never waits on an animation.
 */
@Composable
internal fun BoxScope.StrandsGestureLayer(
    rows: Int,
    cols: Int,
    boardSize: DpSize,
    trace: () -> Trace,
    onTrace: (TraceStep) -> Unit,
    onTouch: () -> Unit,
) {
    val currentTrace by rememberUpdatedState(trace)
    val currentOnTrace by rememberUpdatedState(onTrace)
    val currentOnTouch by rememberUpdatedState(onTouch)
    Box(
        Modifier
            .matchParentSize()
            .pointerInput(rows, cols, boardSize) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    currentOnTouch()
                    val boardWidth = boardSize.width.toPx()
                    val boardHeight = boardSize.height.toPx()
                    val origin = Offset((size.width - boardWidth) / 2, (size.height - boardHeight) / 2)
                    val geometry = GridGeometry(rows, cols, boardWidth, boardHeight)
                    var previous = down.position - origin
                    val start = StrandsHitTest.cellAt(geometry, previous.x, previous.y)
                    if (start == null) {
                        currentOnTrace(TraceInput.outside())
                        return@awaitEachGesture
                    }
                    down.consume()
                    var last: Coord = start
                    var dragging = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val position = change.position - origin
                        forEachInnerCellAlong(geometry, previous, position) { over ->
                            if (over == last) return@forEachInnerCellAlong
                            if (!dragging) {
                                dragging = true
                                currentOnTrace(TraceInput.dragStart(currentTrace(), start))
                            }
                            currentOnTrace(TraceInput.dragTo(currentTrace(), over))
                            last = over
                        }
                        previous = position
                        change.consume()
                    }
                    val ended = currentTrace()
                    currentOnTrace(if (dragging) TraceInput.dragEnd(ended) else TraceInput.tap(ended, start))
                }
            },
    )
}

/**
 * The inner-circle cells a move from [from] to [to] passes, in order. Pointer events arrive
 * sparsely on a fast swipe, so the segment is sampled finer than a hit circle; a cell
 * skipped between two events would otherwise break the path.
 */
private inline fun forEachInnerCellAlong(geometry: GridGeometry, from: Offset, to: Offset, action: (Coord) -> Unit) {
    val step = StrandsBoardSpec.HitCircleFraction * minOf(geometry.cellWidth, geometry.cellHeight) / 2
    val samples = ceil((to - from).getDistance() / step).toInt().coerceAtLeast(1)
    var previous: Coord? = null
    for (i in 1..samples) {
        val point = from + (to - from) * (i.toFloat() / samples)
        val cell = StrandsHitTest.innerCellAt(geometry, point.x, point.y) ?: continue
        if (cell != previous) action(cell)
        previous = cell
    }
}
