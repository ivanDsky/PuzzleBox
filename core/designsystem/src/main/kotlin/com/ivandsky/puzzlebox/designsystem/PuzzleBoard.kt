package com.ivandsky.puzzlebox.designsystem

import com.airbnb.android.showkase.annotation.ShowkaseComposable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

// Maps a board position to a cell. Row 0 is the top, col 0 the left. Positions past
// the edge clamp to the boundary cells so a drag that leaves the board still ends
// on a valid cell instead of silently dropping the gesture.
internal fun cellAt(x: Float, y: Float, sidePx: Float, cellsPerSide: Int): Pair<Int, Int> {
    val col = (x / sidePx * cellsPerSide).toInt().coerceIn(0, cellsPerSide - 1)
    val row = (y / sidePx * cellsPerSide).toInt().coerceIn(0, cellsPerSide - 1)
    return row to col
}

/**
 * n-by-n grid container for puzzle play. Cells are content-agnostic slots; the board
 * owns only the gesture surface (the Zip-style path trace builds on these callbacks —
 * all game logic stays in :core:engine per A16).
 *
 * Tap: [onCellTap] fires on release over a cell. Drag: [onCellDragStart] on the cell
 * where the drag begins (after touch slop), [onCellDrag] each time the finger enters
 * a new cell, [onDragEnd]/[onDragCancel] on release. The touched cell gets a brief
 * highlight because touch-down feedback must be instant (commit happens on release).
 */
@Composable
fun PuzzleBoard(
    cellsPerSide: Int,
    modifier: Modifier = Modifier,
    onCellTap: ((row: Int, col: Int) -> Unit)? = null,
    onCellDragStart: ((row: Int, col: Int) -> Unit)? = null,
    onCellDrag: ((row: Int, col: Int) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    onDragCancel: (() -> Unit)? = null,
    cellContent: @Composable BoxScope.(row: Int, col: Int) -> Unit = { _, _ -> },
) {
    require(cellsPerSide >= 1) { "cellsPerSide must be >= 1, was $cellsPerSide" }
    var pressedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var tracedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var downCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .pointerInput(cellsPerSide) {
                detectTapGestures(
                    // The down recorder above lights the highlight; onPress only has to
                    // clear it when the gesture ends as a plain tap (no drag ran).
                    onPress = { _ ->
                        tryAwaitRelease()
                        pressedCell = null
                    },
                    onTap = { offset ->
                        val (row, col) = cellAt(offset.x, offset.y, size.width.toFloat(), cellsPerSide)
                        onCellTap?.invoke(row, col)
                    },
                )
            }
            .pointerInput(cellsPerSide) {
                // detectDragGestures only reports positions from the slop threshold on,
                // which would skip the cell the finger actually came down on — the path
                // trace must start there, so the down cell is recorded directly.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    downCell = cellAt(down.position.x, down.position.y, size.width.toFloat(), cellsPerSide)
                    pressedCell = downCell
                }
            }
            .pointerInput(cellsPerSide) {
                fun cellOf(offset: Offset) =
                    cellAt(offset.x, offset.y, size.width.toFloat(), cellsPerSide)

                detectDragGestures(
                    onDragStart = { offset ->
                        val cell = downCell ?: cellOf(offset)
                        tracedCell = cell
                        pressedCell = cell
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCellDragStart?.invoke(cell.first, cell.second)
                    },
                    onDrag = { change, _ ->
                        val cell = cellOf(change.position)
                        if (cell != tracedCell) {
                            tracedCell = cell
                            pressedCell = cell
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onCellDrag?.invoke(cell.first, cell.second)
                        }
                    },
                    onDragEnd = {
                        tracedCell = null
                        pressedCell = null
                        onDragEnd?.invoke()
                    },
                    onDragCancel = {
                        tracedCell = null
                        pressedCell = null
                        onDragCancel?.invoke()
                    },
                )
            },
    ) {
        Column(Modifier.matchParentSize()) {
            repeat(cellsPerSide) { row ->
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    repeat(cellsPerSide) { col ->
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            cellContent(row, col)
                        }
                    }
                }
            }
        }
        val gridColor = MaterialTheme.colorScheme.outlineVariant
        Canvas(Modifier.matchParentSize()) {
            val step = size.width / cellsPerSide
            for (i in 1 until cellsPerSide) {
                drawLine(gridColor, Offset(i * step, 0f), Offset(i * step, size.height), strokeWidth = 1.dp.toPx())
                drawLine(gridColor, Offset(0f, i * step), Offset(size.width, i * step), strokeWidth = 1.dp.toPx())
            }
        }
        // A whisper of the accent, not a state color: pressed feedback only.
        val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    pressedCell?.let { (row, col) ->
                        val step = size.width / cellsPerSide
                        drawRect(highlight, topLeft = Offset(col * step, row * step), size = Size(step, step))
                    }
                },
        )
    }
}

// Light/dark previews (ticket-01 pattern: module-local @PuzzleBoxPreviews, PuzzleBoxTheme,
// internal composables — Showkase 1.0.5 cannot index androidx jar-packaged multipreviews).
// A8: the board's only motion is the touch-driven pressed-cell highlight (pointer state
// drawn via drawBehind); it has no programmatic trigger a preview could start, so the
// panes show the resting grid and interactive mode runs the highlight on tap/drag —
// same honest path as the ticket-01 press-scale spring (see reports/01-report.md).
@ShowkaseComposable(name = "PuzzleBoard", group = "PuzzleBoard")
@PuzzleBoxPreviews
@Composable
internal fun PuzzleBoardPreview() {
    PuzzleBoxTheme {
        PuzzleBoard(
            cellsPerSide = 3,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@ShowkaseComposable(name = "PuzzleBoard (sample tiles)", group = "PuzzleBoard")
@PuzzleBoxPreviews
@Composable
internal fun PuzzleBoardSampleTilesPreview() {
    PuzzleBoxTheme {
        // Sample board data defined preview-side: a scrambled 15-puzzle, 0 is the gap.
        val tiles = listOf(5, 2, 8, 1, 9, 6, 3, 12, 7, 4, 15, 10, 13, 11, 14, 0)
        PuzzleBoard(
            cellsPerSide = 4,
            modifier = Modifier.padding(16.dp),
        ) { row, col ->
            when (val number = tiles[row * 4 + col]) {
                0 -> Unit
                else -> Box(
                    Modifier
                        .padding(3.dp)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$number",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}
