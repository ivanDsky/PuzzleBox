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
import com.ivandsky.puzzlebox.games.strandsui.Trace
import com.ivandsky.puzzlebox.games.strandsui.TraceInput
import com.ivandsky.puzzlebox.games.strandsui.TraceStep
import com.ivandsky.puzzlebox.gameui.GridGeometry

/**
 * Strands' own input (A12), laid over the whole board area with the [boardSize] board
 * centred in it. It only maps positions to cells; which letter a drag joins is
 * [StrandsTraceWalk]'s, what joining does is [TraceInput]'s.
 *
 * A touch-down on a cell waits: lifting before the walk joins another letter is a
 * [TraceInput.tap]; joining one turns the gesture into a drag that starts at the first
 * cell. A down beside the board clears the trace. [onTouch] fires on every down, before any
 * step, so the board can retire running motion: input never waits on an animation. While a
 * drag runs, [onFinger] gets the finger for the live tip, and null once it lifts.
 */
@Composable
internal fun BoxScope.StrandsGestureLayer(
    rows: Int,
    cols: Int,
    boardSize: DpSize,
    trace: () -> Trace,
    onTrace: (TraceStep) -> Unit,
    onTouch: () -> Unit,
    onFinger: (TraceFinger?) -> Unit,
) {
    val currentTrace by rememberUpdatedState(trace)
    val currentOnTrace by rememberUpdatedState(onTrace)
    val currentOnTouch by rememberUpdatedState(onTouch)
    val currentOnFinger by rememberUpdatedState(onFinger)
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
                    fun inCells(position: Offset) =
                        (position - origin).let { Offset(it.x / geometry.cellWidth, it.y / geometry.cellHeight) }
                    val downAt = down.position - origin
                    val start = StrandsHitTest.cellAt(geometry, downAt.x, downAt.y)
                    if (start == null) {
                        currentOnTrace(TraceInput.outside())
                        return@awaitEachGesture
                    }
                    down.consume()
                    val alone = listOf(start)
                    val walk = StrandsTraceWalk(rows, cols, inCells(down.position))
                    var dragging = false
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val at = inCells(change.position)
                            walk.moveTo(at, { if (dragging) currentTrace().path else alone }) { over ->
                                if (!dragging) {
                                    dragging = true
                                    currentOnTrace(TraceInput.dragStart(start))
                                }
                                currentOnTrace(TraceInput.dragTo(currentTrace(), over))
                            }
                            if (dragging) currentOnFinger(TraceFinger(at, walk.travel()))
                            change.consume()
                        }
                    } finally {
                        currentOnFinger(null)
                    }
                    val ended = currentTrace()
                    currentOnTrace(if (dragging) TraceInput.dragEnd(ended) else TraceInput.tap(ended, start))
                }
            },
    )
}
