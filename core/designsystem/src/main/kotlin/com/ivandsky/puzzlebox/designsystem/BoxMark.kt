package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// The four-tile Box mark (identity §2.1/§2.5): "the box of puzzles", a 2×2 grid of
// rounded tiles on the 24 grid. It is the Play tab's icon: outlined (6dp tiles, 2dp round
// stroke) when unselected, filled (8dp tiles) when selected. Both cover 3..21 with a 2dp
// gap, so the swap never shifts the silhouette. Colour comes from the Icon tint.

private const val GRID = 24f
private const val STROKE = 2f

private val BoxTilesOutlined = addPathNodes(
    "M6 4H8A2 2 0 0 1 10 6V8A2 2 0 0 1 8 10H6A2 2 0 0 1 4 8V6A2 2 0 0 1 6 4Z" +
        "M16 4H18A2 2 0 0 1 20 6V8A2 2 0 0 1 18 10H16A2 2 0 0 1 14 8V6A2 2 0 0 1 16 4Z" +
        "M6 14H8A2 2 0 0 1 10 16V18A2 2 0 0 1 8 20H6A2 2 0 0 1 4 18V16A2 2 0 0 1 6 14Z" +
        "M16 14H18A2 2 0 0 1 20 16V18A2 2 0 0 1 18 20H16A2 2 0 0 1 14 18V16A2 2 0 0 1 16 14Z",
)

private val BoxTilesFilled = addPathNodes(
    "M5.5 3H8.5A2.5 2.5 0 0 1 11 5.5V8.5A2.5 2.5 0 0 1 8.5 11H5.5A2.5 2.5 0 0 1 3 8.5V5.5A2.5 2.5 0 0 1 5.5 3Z" +
        "M15.5 3H18.5A2.5 2.5 0 0 1 21 5.5V8.5A2.5 2.5 0 0 1 18.5 11H15.5A2.5 2.5 0 0 1 13 8.5V5.5A2.5 2.5 0 0 1 15.5 3Z" +
        "M5.5 13H8.5A2.5 2.5 0 0 1 11 15.5V18.5A2.5 2.5 0 0 1 8.5 21H5.5A2.5 2.5 0 0 1 3 18.5V15.5A2.5 2.5 0 0 1 5.5 13Z" +
        "M15.5 13H18.5A2.5 2.5 0 0 1 21 15.5V18.5A2.5 2.5 0 0 1 18.5 21H15.5A2.5 2.5 0 0 1 13 18.5V15.5A2.5 2.5 0 0 1 15.5 13Z",
)

private fun markBuilder(name: String) = ImageVector.Builder(
    name = name,
    defaultWidth = GRID.dp,
    defaultHeight = GRID.dp,
    viewportWidth = GRID,
    viewportHeight = GRID,
)

internal val BoxMarkOutlined: ImageVector = markBuilder("BoxMark.Outlined")
    .addPath(
        pathData = BoxTilesOutlined,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    .build()

internal val BoxMarkFilled: ImageVector = markBuilder("BoxMark.Filled")
    .addPath(pathData = BoxTilesFilled, fill = SolidColor(Color.Black))
    .build()
