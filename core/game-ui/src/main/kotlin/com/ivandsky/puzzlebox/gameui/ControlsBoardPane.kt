package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.Spacing

private enum class PaneSlot { Toolbar, Board }

/**
 * The board pane (nav-rethink B5, B6): [board] at most [cap] wide with [controls] directly under it, as wide as
 * the board, or with [sideColumn] stacked in a column to its right. The group is centred in the pane.
 *
 * [board] gets its side as `paneCap`: the smaller of [cap] and what the pane leaves beside the
 * controls, less the [PlayBoardPane] insets and side gutters. Its slot is at least that square,
 * so a board that is loading or shorter than its cap does not move the controls; taller
 * content (Sudoku's keypad) grows into the height left. The side needs the controls' size
 * first, hence the subcomposition.
 */
@Composable
internal fun ControlsBoardPane(
    controls: (@Composable () -> Unit)?,
    board: @Composable BoxScope.(paneCap: Dp) -> Unit,
    cap: Dp,
    sideColumn: Boolean,
    modifier: Modifier,
) {
    val beside = sideColumn && controls != null
    SubcomposeLayout(modifier) { constraints ->
        val width = constraints.maxWidth
        val maxHeight = constraints.maxHeight
        val inset = PlayBoardInset.roundToPx()
        val bar = if (controls != null) {
            subcompose(PaneSlot.Toolbar) {
                CompositionLocalProvider(LocalPlayControlsVertical provides beside) {
                    Box(Modifier.testTag("play.controls")) { controls() }
                }
            }
        } else {
            emptyList()
        }
        // What the controls take from the board: a column's width and its gutter, or a row's
        // height and the margin that closes the pane under it.
        val columnWidth = if (beside) bar.maxOf { it.maxIntrinsicWidth(maxHeight) } else 0
        val rowHeight = if (beside) 0 else bar.maxOfOrNull { it.maxIntrinsicHeight(width) } ?: 0
        val closing = if (rowHeight > 0) Spacing.sm.roundToPx() else 0
        val boardWidth = if (beside) width - columnWidth - inset else width
        val boardHeight = (maxHeight - rowHeight - closing).coerceAtLeast(0)
        val side = minOf(cap.roundToPx(), boardWidth - 2 * inset, boardHeight - 2 * inset).coerceAtLeast(0)
        val sideDp = side.toDp()
        val slotWidth = if (beside) side + 2 * inset else width
        val slot = Constraints(slotWidth, slotWidth, (side + 2 * inset).coerceAtMost(boardHeight), boardHeight)
        val boardPlaceables = subcompose(PaneSlot.Board) {
            Box(propagateMinConstraints = true) { board(sideDp) }
        }.map { it.measure(slot) }
        val slotHeight = boardPlaceables.maxOfOrNull { it.height } ?: 0
        val barConstraints = if (beside) {
            Constraints(minWidth = columnWidth, maxWidth = columnWidth, maxHeight = maxHeight)
        } else {
            Constraints(minWidth = side, maxWidth = side, maxHeight = rowHeight)
        }
        val barPlaceables = bar.map { it.measure(barConstraints) }
        val stack = if (beside) slotHeight else slotHeight + rowHeight + closing
        val height = stack.coerceIn(constraints.minHeight, maxHeight)
        layout(width, height) {
            val top = (height - stack) / 2
            if (beside) {
                val left = (width - slotWidth - columnWidth - inset) / 2
                boardPlaceables.forEach { it.placeRelative(left, top) }
                barPlaceables.forEach { it.placeRelative(left + slotWidth, (height - it.height) / 2) }
            } else {
                boardPlaceables.forEach { it.placeRelative(0, top) }
                // Rounded as the pane's Box centres its board, so the row's edges meet the board's.
                val rowLeft = Alignment.CenterHorizontally.align(side, width, layoutDirection)
                barPlaceables.forEach { it.placeRelative(rowLeft, top + slotHeight) }
            }
        }
    }
}
