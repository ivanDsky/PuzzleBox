package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.toSize
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import kotlin.math.ceil

/** What the board's touch handling shows while a finger is down: where it lifted, whether it drags, and the live stroke. */
@Stable
internal class QueensBoardTouch {
    /** Where the last tap or drag lifted, in board pixels; the ring opens there. */
    var lift by mutableStateOf(Offset.Zero)

    /** A drag is under way; the ring stays shut until it lifts. */
    var dragging by mutableStateOf(false)
        private set

    /** The cell a colour stroke started on, or null when no stroke is under way. */
    var strokeFrom by mutableStateOf<Int?>(null)
        private set

    /** The cells the stroke has crossed so far, crowns included, in order. */
    var strokeCells by mutableStateOf<List<Int>>(emptyList())
        private set

    /** [board] as the stroke would leave it: the start's colour over every crossed cell without a crown. */
    fun preview(board: QueensDraft): QueensDraft {
        val from = strokeFrom ?: return board
        val region = board.regions[from]
        val painted = strokeCells.filterTo(HashSet()) { it !in board.crowns }
        return board.copy(regions = board.regions.mapIndexed { idx, it -> if (idx in painted) region else it })
    }

    /** The cells the stroke preview recolours, tagged `.stroke` on the board. */
    fun stroked(board: QueensDraft): Set<Int> =
        if (strokeFrom == null) emptySet() else strokeCells.filterTo(HashSet()) { it !in board.crowns }

    fun start(dragFrom: Int, stroke: Boolean) {
        dragging = true
        strokeFrom = if (stroke) dragFrom else null
        strokeCells = if (stroke) listOf(dragFrom) else emptyList()
    }

    fun extend(cells: List<Int>) {
        strokeCells = (strokeCells + cells).distinct()
    }

    fun stop() {
        dragging = false
        strokeFrom = null
        strokeCells = emptyList()
    }
}

/**
 * The editor board's touch handling over an [n]×[n] board. A tap hits the cell under it
 * (edge-clamped, as in play) and goes to [QueensEditorController.tap]. A drag that starts on a
 * coloured cell, in either mode, is a colour stroke: [touch] previews it live and the lift
 * commits it as one [QueensEditorController.stroke]. A drag from an unpainted cell adds every
 * cell it crosses to the selection in Paint mode and does nothing in Crown mode.
 */
internal fun Modifier.queensBoardGestures(controller: QueensEditorController, n: Int, touch: QueensBoardTouch): Modifier = this
    .pointerInput(controller, n) {
        detectTapGestures(onTap = { offset ->
            touch.lift = offset
            controller.tap(cellAt(offset, size.toSize(), n))
        })
    }
    .pointerInput(controller, n) {
        var last = Offset.Zero
        var selecting = false
        detectDragGestures(
            onDragStart = { start ->
                last = start
                val cell = cellAt(start, size.toSize(), n)
                val board = controller.draft.value.state.draft as QueensDraft
                val stroke = board.regions[cell] != QueensDraft.UNPAINTED
                selecting = !stroke && controller.mode.value == QueensEditorMode.Paint
                touch.start(cell, stroke)
                if (selecting) controller.add(listOf(cell))
            },
            onDragEnd = {
                val from = touch.strokeFrom
                if (from != null) controller.stroke(from, touch.strokeCells)
                touch.stop()
                touch.lift = last
            },
            onDragCancel = { touch.stop() },
        ) { change, _ ->
            val cells = crossed(last, change.position, size.toSize(), n)
            last = change.position
            when {
                touch.strokeFrom != null -> touch.extend(cells)
                selecting -> controller.add(cells)
            }
        }
    }

/** Adds the [cells] a drag crossed; a cell already selected stays selected. */
private fun QueensEditorController.add(cells: List<Int>) {
    cells.forEach { if (it !in selection.value) tap(it) }
}

private fun cellAt(offset: Offset, size: Size, n: Int): Int {
    val col = (offset.x / size.width * n).toInt().coerceIn(0, n - 1)
    val row = (offset.y / size.height * n).toInt().coerceIn(0, n - 1)
    return row * n + col
}

/** Every cell on the segment [from]→[to], sampled at a quarter cell so a fast drag skips none. */
private fun crossed(from: Offset, to: Offset, size: Size, n: Int): List<Int> {
    val step = minOf(size.width, size.height) / n / 4
    val samples = ceil((to - from).getDistance() / step).toInt().coerceAtLeast(1)
    return (0..samples).map { cellAt(from + (to - from) * (it / samples.toFloat()), size, n) }.distinct()
}
