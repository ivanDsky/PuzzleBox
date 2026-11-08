package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Game events out of the trace gesture (user gate feedback R1 — ticket 26): a stroke
 * never cancels or penalizes wholesale. Valid next-cell steps extend; invalid cells leave
 * the trace untouched (reported as [DragEvent.Refused]) so the finger may wander without
 * penalty; lifting the finger keeps the trace
 * exactly as drawn. Retraction is deliberate: a tap on a traced cell, or drawing back over
 * the line (any traced cell, see [DragTraceBoard]).
 */
sealed interface DragEvent {
    /** Finger dragged onto [cell] and the game's canExtend predicate allowed it. */
    data class Extend(val cell: GridCell) : DragEvent

    /** Retraction to a traced cell; the path trims to include [index] (the new tail). */
    data class Retract(val index: Int) : DragEvent

    /** Tap on the first traced cell: the trace empties to the start state. */
    data object Clear : DragEvent

    /**
     * Finger dragged onto an untraced [cell] the canExtend predicate refused. The trace
     * is unchanged; the game may explain the refusal (spec A7) or ignore it (R1: the
     * finger wanders without penalty, so this event never changes state on its own).
     */
    data class Refused(val cell: GridCell) : DragEvent
}

/**
 * The validation hooks (finding F3-③): game-supplied predicates over the trace path, so
 * one DragTrace serves Zip's wall-respecting 4-way trace AND Strands' 8-way word trace
 * without forking the gesture layer. The predicate never mutates state; retraction
 * (draw-back by one, tap-to-cell) is gesture-layer law, identical for every trace game.
 */
fun interface DragTraceHooks {
    /** May the trace step onto [next] given [path]? (adjacency/walls/revisit — game rules) */
    fun canExtend(path: List<GridCell>, next: GridCell): Boolean
}

/**
 * Board + drag-trace gesture surface: the Zip/Strands input component. The working trace
 * is anchored at the committed [path]'s end. Gesture contract (R1):
 *
 * - **Drawing never cancels.** Past the touch slop the stroke is a drag: hook-approved
 *   cells extend, everything else (wrong/non-adjacent) is reported Refused. Drawing onto
 *   a traced cell cuts the trace back to it: the previous cell undoes one cell, an earlier
 *   one undoes down to it, and a stroke that starts on a traced cell picks the line up there.
 *   Cells take effect by the finger's catch shape, not their bare rect ([DragTraceStroke]).
 *   Releasing keeps the trace exactly as drawn; no event fires on release.
 * - **Tap retracts.** Down+up within the touch slop on a traced cell trims the path back
 *   through it ([DragEvent.Retract]); on the first traced cell it empties the trace to
 *   the start state ([DragEvent.Clear]); a tap anywhere else is a no-op. A tap never
 *   extends.
 *
 * Hooks are pure predicates — committed state changes happen only after the screen
 * validates the emitted events against the game's rules.
 */
@Composable
fun DragTraceBoard(
    rows: Int,
    cols: Int,
    path: List<GridCell>,
    hooks: DragTraceHooks,
    onEvent: (DragEvent) -> Unit,
    modifier: Modifier = Modifier,
    edgeOverlays: List<EdgeOverlay> = emptyList(),
    underlay: @Composable BoxScope.() -> Unit = {},
    cellContent: @Composable BoxScope.(row: Int, col: Int) -> Unit = { _, _ -> },
) {
    val latestPath by rememberUpdatedState(path)
    val latestHooks by rememberUpdatedState(hooks)
    val latestOnEvent by rememberUpdatedState(onEvent)

    Box(modifier) {
        // Plain modifier: BoardGrid's aspectRatio self-sizes the board under the caller's
        // (possibly only max-bounded) constraints and defines this Box's size — the gesture
        // layer below matches it. matchParentSize here would collapse the board to 0 when
        // the caller caps the size with widthIn/heightIn (A4).
        BoardGrid(
            rows = rows,
            cols = cols,
            edgeOverlays = edgeOverlays,
            underlay = underlay,
            cellContent = cellContent,
        )
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(rows, cols) {
                    val slop = viewConfiguration.touchSlop.toFloat()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val geometry = GridGeometry(rows, cols, size.width.toFloat(), size.height.toFloat())
                        val stroke = DragTraceStroke(geometry, latestHooks, latestPath, down.position)
                        val downCell = geometry.cellAt(down.position)
                        var dragged = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                if (!dragged) {
                                    if ((change.position - down.position).getDistance() <= slop) {
                                        change.consume()
                                        continue // still a candidate tap
                                    }
                                    // Drag confirmed: the stroke starts where the finger landed
                                    // (empty path + start cell, or the tail), then follows it.
                                    dragged = true
                                    stroke.begin().forEach(latestOnEvent)
                                }
                                stroke.moveTo(change.historical.map { it.position } + change.position).forEach(latestOnEvent)
                            } else {
                                if (!dragged) {
                                    // Tap: deliberate retraction only — traced cell trims back
                                    // through it, the first traced cell empties, elsewhere and
                                    // on the tail it is a no-op. Release itself emits nothing:
                                    // the trace stays exactly as drawn (R1).
                                    when (val index = stroke.path.indexOf(downCell)) {
                                        -1, stroke.path.lastIndex -> Unit
                                        0 -> latestOnEvent(DragEvent.Clear)
                                        else -> latestOnEvent(DragEvent.Retract(index))
                                    }
                                }
                                break
                            }
                            change.consume()
                        }
                    }
                },
        )
    }
}
