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
 * centred in it. It only maps positions to cells; what a touch on a letter makes of the
 * trace is [StrandsDragSession]'s, what each step does is [TraceInput]'s.
 *
 * A down beside the board clears the trace. [onTouch] fires on every down, before any
 * step, so the board can retire running motion: input never waits on an animation. A size
 * change mid-gesture does not restart it; positions map with the size in force.
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
    val currentBoardSize by rememberUpdatedState(boardSize)
    val currentOnTrace by rememberUpdatedState(onTrace)
    val currentOnTouch by rememberUpdatedState(onTouch)
    Box(
        Modifier
            .matchParentSize()
            .pointerInput(rows, cols) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    currentOnTouch()
                    fun origin() = Offset(
                        (size.width - currentBoardSize.width.toPx()) / 2,
                        (size.height - currentBoardSize.height.toPx()) / 2,
                    )
                    fun geometry() = GridGeometry(
                        rows, cols, currentBoardSize.width.toPx(), currentBoardSize.height.toPx(),
                    )
                    fun inCells(position: Offset): Offset {
                        val geometry = geometry()
                        val inBoard = position - origin()
                        return Offset(inBoard.x / geometry.cellWidth, inBoard.y / geometry.cellHeight)
                    }
                    val downAt = down.position - origin()
                    val start = StrandsHitTest.cellAt(geometry(), downAt.x, downAt.y)
                    if (start == null) {
                        currentOnTrace(TraceInput.outside())
                        return@awaitEachGesture
                    }
                    down.consume()
                    val session = StrandsDragSession(rows, cols, start, inCells(down.position))
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        session.move(inCells(change.position), currentTrace()).forEach { currentOnTrace(it) }
                        change.consume()
                    }
                    currentOnTrace(session.end(currentTrace()))
                }
            },
    )
}
