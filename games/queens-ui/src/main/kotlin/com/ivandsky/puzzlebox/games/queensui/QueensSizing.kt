package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.gameui.PlayBoardMaxWidth

/**
 * §6 board sizing policy — pure, computed, never eyeballed. The board side is the
 * smaller of (a) the gesture surface's available pane space after chrome and
 * [panePadding], and (b) [maxSide], the readability cap ([queensBoardMaxSideFor]). N
 * (6–11 per A9) changes cell count only, never the layout rule. Tablet-first: the same
 * function serves the TwoPane detail pane via the same min() — only the reserved chrome
 * constants differ at the call site.
 *
 * Floor: the result never drops below cellsPerSide × [QueensBoardDefaults.cellMinSize]
 * unless the pane itself is smaller — the board-level gesture surface (clamped cellAt,
 * per-cell-enter haptics, instant pressed highlight) is the touch target, per §6.
 * The floor never forces the board above the pane (spec A9: a 12×12 fits 360dp).
 */
fun queensBoardSideFor(
    paneWidth: Dp,
    paneHeight: Dp,
    reservedTop: Dp,
    reservedBottom: Dp,
    cellsPerSide: Int,
    panePadding: Dp = queensPanePaddingFor(cellsPerSide),
    maxSide: Dp = queensBoardMaxSideFor(cellsPerSide),
): Dp {
    val widthBound = paneWidth - panePadding
    val heightBound = paneHeight - reservedTop - reservedBottom - panePadding
    val available = minOf(widthBound, heightBound)
    val floor = QueensBoardDefaults.cellMinSize * cellsPerSide
    // Cap by readability, floor by the gesture surface, never above what the pane offers.
    return minOf(available, maxSide).coerceAtLeast(minOf(available, floor))
}

/**
 * The §6 readability cap on the board side: [QueensBoardDefaults.cellMaxSize] per cell
 * from [QueensBoardDefaults.perCellCapFromCells] cells per side (every daily). A smaller
 * board — the 4×4 lessons — would read as a postage stamp under the per-cell cap, so it
 * fills its pane up to [PlayBoardMaxWidth], the cap Tango's and Zip's boards share.
 */
fun queensBoardMaxSideFor(cellsPerSide: Int): Dp =
    if (cellsPerSide >= QueensBoardDefaults.perCellCapFromCells) {
        QueensBoardDefaults.cellMaxSize * cellsPerSide
    } else {
        PlayBoardMaxWidth
    }

/** Total gutter around the daily board: 16dp per side, shrinking to 8dp from
 *  [QueensBoardDefaults.compactGutterFromCells] cells so dense boards keep their cells. */
fun queensPanePaddingFor(cellsPerSide: Int): Dp =
    if (cellsPerSide >= QueensBoardDefaults.compactGutterFromCells) {
        QueensBoardDefaults.compactGutter * 2
    } else {
        QueensBoardDefaults.gutter * 2
    }
