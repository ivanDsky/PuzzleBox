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
 * Game events out of the trace gesture. Extend/Retract steps are pre-filtered by the
 * game's own [DragTraceHooks] predicates; [Release] fires on EVERY gesture end (tap or
 * drag) so the screen can run authoritative validation — a release that produced no
 * Extend for its cell is the invalid-drop signal the game turns into rejection feedback.
 */
sealed interface DragEvent {
    /** Finger stepped (or tapped) onto [cell] and the game's canExtend predicate allowed it. */
    data class Extend(val cell: GridCell) : DragEvent

    /** Finger dragged back onto an earlier trace cell; the path trims to include [index]. */
    data class Retract(val index: Int) : DragEvent

    /** Gesture ended over [cell]. Always emitted, exactly once per gesture. */
    data class Release(val cell: GridCell) : DragEvent
}

/**
 * The validation hooks (finding F3-③): game-supplied predicates over the trace path, so
 * one DragTrace serves Zip's wall-respecting 4-way trace AND Strands' 8-way word trace
 * without forking the gesture layer. The predicates never mutate state.
 */
interface DragTraceHooks {
    /** May the trace step onto [next] given [path]? (adjacency/walls/revisit — game rules) */
    fun canExtend(path: List<GridCell>, next: GridCell): Boolean

    /** Is [next] a retraction target? Return its index in [path] to trim to, or null.
     *  Checked BEFORE [canExtend]: dragging back over the path retracts (Zip §10 ruling). */
    fun retractIndex(path: List<GridCell>, next: GridCell): Int?
}

/**
 * Board + drag-trace gesture surface: the Zip/Strands input component. The working trace
 * is anchored at the committed [path]'s end; during one gesture, extends append and
 * retracts trim, so a single finger stroke can run forward and back. Hooks are pure
 * predicates — committed state changes happen only after the screen validates the emitted
 * events against the game's rules.
 *
 * Adjacent-tap (Zip §10 ruling): a tap on a cell the hooks allow extends once; taps the
 * hooks reject surface only as [DragEvent.Release] for authoritative rejection handling.
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
    cellContent: @Composable BoxScope.(row: Int, col: Int) -> Unit = { _, _ -> },
) {
    val latestPath by rememberUpdatedState(path)
    val latestHooks by rememberUpdatedState(hooks)
    val latestOnEvent by rememberUpdatedState(onEvent)

    Box(modifier.aspectRatio(ratio = cols.toFloat() / rows.toFloat())) {
        BoardGrid(
            rows = rows,
            cols = cols,
            modifier = Modifier.matchParentSize(),
            edgeOverlays = edgeOverlays,
            cellContent = cellContent,
        )
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(rows, cols) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val geometry = GridGeometry(rows, cols, size.width.toFloat(), size.height.toFloat())
                        var workingPath = latestPath.toList()

                        // The touch-down cell is the first evaluated step: a drag starting on
                        // the start cell (empty path) or tail (committed path) begins the
                        // trace on contact; touching an earlier path cell retracts to it.
                        var currentCell = geometry.cellAt(down.position)
                        val firstStep = stepPath(workingPath, currentCell, latestHooks)
                        if (firstStep != null) {
                            workingPath = firstStep.first
                            latestOnEvent(firstStep.second)
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            val cell = geometry.cellAt(change.position)

                            if (!change.pressed) {
                                // Gesture end: every gesture reports Release exactly once, so
                                // the screen can run authoritative validation (an unextended
                                // release cell is the invalid-drop signal).
                                latestOnEvent(DragEvent.Release(cell))
                                break
                            }

                            if (cell != currentCell) {
                                val step = stepPath(workingPath, cell, latestHooks)
                                if (step != null) {
                                    workingPath = step.first
                                    latestOnEvent(step.second)
                                }
                                currentCell = cell
                            }
                            change.consume()
                        }
                    }
                },
        )
    }
}

/** Applies one cell entry to the working path via the game's hooks; null if neither
 *  retraction nor extension applies (the gesture simply does not progress there). */
private fun stepPath(
    path: List<GridCell>,
    next: GridCell,
    hooks: DragTraceHooks,
): Pair<List<GridCell>, DragEvent>? {
    hooks.retractIndex(path, next)?.let { index ->
        if (index in path.indices && index < path.lastIndex) {
            return path.subList(0, index + 1).toList() to DragEvent.Retract(index)
        }
        return null // retracting to the tail is a no-op, not an event
    }
    if (next != path.lastOrNull() && hooks.canExtend(path, next)) {
        return (path + next) to DragEvent.Extend(next)
    }
    return null
}
