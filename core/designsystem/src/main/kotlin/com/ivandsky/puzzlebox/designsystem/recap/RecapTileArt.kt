package com.ivandsky.puzzlebox.designsystem.recap

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** What a cover tile draws on its dark art plate: a game-colour mosaic, or a year's twelve months as bars. */
sealed interface RecapTileArt {
    /** [cells] colours in reading order, [MOSAIC_CELLS] of them (fewer leave [empty] cells). */
    class Mosaic(val cells: List<Color>, val empty: Color) : RecapTileArt

    /** One bar per month, [months] counts, in [color]. */
    class Bars(val months: List<Int>, val color: Color) : RecapTileArt
}

internal val ArtSize: Dp = 88.dp
