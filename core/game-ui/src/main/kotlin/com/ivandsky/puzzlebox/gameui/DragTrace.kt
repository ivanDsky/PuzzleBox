package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Game events out of the trace gesture (user gate feedback R1 — ticket 26): a stroke
 * never cancels or penalizes wholesale. Valid next-cell steps extend; invalid cells are
 * ignored so the finger may wander without penalty; lifting the finger keeps the trace
 * exactly as drawn. Deliberate retraction happens only through a tap on a traced cell
 * (or by drawing onto the immediately-previous cell, preserved for mid-gesture
 * reversibility).
 */
sealed interface DragEvent {
    /** Finger dragged onto [cell] and the game's canExtend predicate allowed it. */
    data class Extend(val cell: GridCell) : DragEvent

    /** Retraction to a traced cell; the path trims to include [index] (the new tail). */
    data class Retract(val index: Int) : DragEvent

    /** Tap on the first traced cell: the trace empties to the start state. */
    data object Clear : DragEvent
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
 *   cells extend, everything else (occupied/wrong/non-adjacent) is ignored. Drawing onto
 *   the previous cell in path order retracts by one (apple interruptibility); drawing
 *   onto any earlier traced cell is ignored — crossing the path never slashes it.
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
                        var workingPath = latestPath.toList()
                        val downCell = geometry.cellAt(down.position)
                        var dragged = false
                        var currentCell = downCell

                        /** One drag step: previous-cell draw-back retracts by one, hook-approved
                         *  cells extend, everything else is ignored without penalty. */
                        fun dragStep(cell: GridCell) {
                            val trimmed = workingPath.dropLast(1)
                            when {
                                cell == workingPath.lastOrNull() -> Unit // tail re-entry
                                cell == trimmed.lastOrNull() -> { // previous cell → retract one
                                    workingPath = trimmed
                                    latestOnEvent(DragEvent.Retract(trimmed.lastIndex))
                                }
                                cell !in workingPath && latestHooks.canExtend(workingPath, cell) -> {
                                    workingPath = workingPath + cell
                                    latestOnEvent(DragEvent.Extend(cell))
                                }
                                // else: an earlier traced cell or a hook-rejected cell — ignored,
                                // the finger wanders without penalty (R1).
                            }
                        }

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
                                    dragStep(downCell)
                                    val cell = geometry.cellAt(change.position)
                                    if (cell != currentCell) {
                                        currentCell = cell
                                        dragStep(cell)
                                    }
                                } else {
                                    val cell = geometry.cellAt(change.position)
                                    if (cell != currentCell) {
                                        currentCell = cell
                                        dragStep(cell)
                                    }
                                }
                            } else {
                                if (!dragged) {
                                    // Tap: deliberate retraction only — traced cell trims back
                                    // through it, the first traced cell empties, elsewhere and
                                    // on the tail it is a no-op. Release itself emits nothing:
                                    // the trace stays exactly as drawn (R1).
                                    when (val index = workingPath.indexOf(downCell)) {
                                        -1, workingPath.lastIndex -> Unit
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
