package com.ivandsky.puzzlebox.designsystem.recap

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The layouts of a [RecapCoverTile]: [Full] is Today's 216dp tile, at most 560dp wide with the open button; [Year] (124dp,
 * bars) and [Month] (176dp, mosaic) are the hub's compact tiles, as wide as their grid cell.
 */
enum class RecapTileSize(internal val minHeight: Dp, internal val maxWidth: Dp) {
    Full(216.dp, 560.dp),
    Year(124.dp, Dp.Infinity),
    Month(176.dp, Dp.Infinity),
}
