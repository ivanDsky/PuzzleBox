package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * §6 board sizing policy — pure, computed, never eyeballed. The board side is the
 * smaller of (a) the gesture surface's available pane space after chrome, and (b) the
 * readability cap ([QueensBoardDefaults.cellMaxSize] per cell). N (6–11 per A9) changes
 * cell count only, never the layout rule. Tablet-first: the same function serves the
 * TwoPane detail pane via the same min() — only the reserved chrome constants differ
 * at the call site.
 *
 * Floor: the result never drops below cellsPerSide × [QueensBoardDefaults.cellMinSize]
 * unless the pane itself is smaller — the board-level gesture surface (clamped cellAt,
 * per-cell-enter haptics, instant pressed highlight) is the touch target, per §6.
 */
fun queensBoardSideFor(
    paneWidth: Dp,
    paneHeight: Dp,
    reservedTop: Dp,
    reservedBottom: Dp,
    cellsPerSide: Int,
    panePadding: Dp = 16.dp * 2,
): Dp {
    val widthBound = paneWidth - panePadding
    val heightBound = paneHeight - reservedTop - reservedBottom - panePadding
    val available = minOf(widthBound, heightBound)
    val cap = QueensBoardDefaults.cellMaxSize * cellsPerSide
    val floor = QueensBoardDefaults.cellMinSize * cellsPerSide
    // Cap by readability, floor by the gesture surface, never above what the pane offers.
    return minOf(available, cap).coerceAtLeast(minOf(available, floor))
}
