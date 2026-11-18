package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The widest a play board renders on a phone (nav-rethink B6), and the default cap of a board drawn outside the
 * play scaffold (the tutorial, a replay). It is a MAX, not a fixed size: phones keep near-full width under the
 * pane's standard margins, and a shorter pane shrinks the board further so the grid aspect stays exact.
 */
val PhoneBoardCap: Dp = 552.dp

private val TabletBoardCap: Dp = 640.dp

/** The widest a grid cell may grow on a tablet class, so a board is at most this times the grid's columns. */
private val TabletCellCap: Dp = 96.dp

/**
 * The largest board side for [deviceClass] on a grid of [gridColumns]: [PhoneBoardCap] on a phone; on a tablet
 * class (portrait or landscape) 640 dp, or [TabletCellCap] times the columns when that is smaller. Null
 * [gridColumns] (a game with no grid to size by) leaves the class cap.
 */
fun boardCapFor(deviceClass: DeviceClass, gridColumns: Int?): Dp {
    if (deviceClass == DeviceClass.Phone) return PhoneBoardCap
    if (gridColumns == null) return TabletBoardCap
    return minOf(TabletBoardCap, TabletCellCap * gridColumns)
}
