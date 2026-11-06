package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.unit.Dp

/**
 * The board's cell pitch for the space it is given (A37, SC11): the largest square cell
 * that fits both [availableWidth] / [cols] and [availableHeight] / [rows], capped at
 * [StrandsBoardSpec.CellMaxSize], so a short tablet landscape pane binds on height.
 *
 * The [StrandsBoardSpec.TouchFloor] holds even when the pane is shorter than 8 rows of it
 * (a phone in landscape): the host scrolls instead of shrinking the targets. An unbounded
 * height ([Dp.Infinity], a scrolling host) sizes from the width alone.
 */
internal fun strandsCellSizeFor(availableWidth: Dp, availableHeight: Dp, rows: Int, cols: Int): Dp {
    require(rows > 0 && cols > 0) { "board must have rows > 0 and cols > 0, got ${rows}x$cols" }
    val fit = minOf(availableWidth / cols, availableHeight / rows, StrandsBoardSpec.CellMaxSize)
    return fit.coerceAtLeast(StrandsBoardSpec.TouchFloor)
}
