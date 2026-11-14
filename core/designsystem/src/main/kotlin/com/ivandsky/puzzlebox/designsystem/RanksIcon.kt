package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// The Ranks tab's mark: a three-step podium on the 24 grid, first place in the middle. Not
// the Trophy, which marks a competition. Unselected it is the outlined silhouette with its
// steps divided (stroke 2dp round over 4..20, like BoxMark's outline); selected it is three
// rounded solid bars with a 2dp gap, no stroke, the way BoxMark's filled tiles and the
// Rounded filled glyphs read. Both cover 3..21, so the swap never shifts the silhouette.
// Extensions rather than members because PuzzleIcons.kt sits near the file-length cap; call
// sites still read `PuzzleIcons.Ranks`. Colour comes from the Icon tint.

private const val GRID = 24f
private const val STROKE = 2f
private const val PODIUM = "M4 20V12H9.33V4H14.67V9H20V20Z"

private val PodiumOutlined = addPathNodes(PODIUM + "M9.33 12V20M14.67 9V20")

// Left 3..7.67 from y 11, middle 9.67..14.33 from y 3, right 16.33..21 from y 8, all down to 21; corners 1.5.
private val PodiumFilled = addPathNodes(
    "M4.5 11H6.17A1.5 1.5 0 0 1 7.67 12.5V19.5A1.5 1.5 0 0 1 6.17 21H4.5A1.5 1.5 0 0 1 3 19.5V12.5A1.5 1.5 0 0 1 4.5 11Z" +
        "M11.17 3H12.83A1.5 1.5 0 0 1 14.33 4.5V19.5A1.5 1.5 0 0 1 12.83 21H11.17A1.5 1.5 0 0 1 9.67 19.5V4.5A1.5 1.5 0 0 1 11.17 3Z" +
        "M17.83 8H19.5A1.5 1.5 0 0 1 21 9.5V19.5A1.5 1.5 0 0 1 19.5 21H17.83A1.5 1.5 0 0 1 16.33 19.5V9.5A1.5 1.5 0 0 1 17.83 8Z",
)

private fun podiumBuilder(name: String) = ImageVector.Builder(
    name = name,
    defaultWidth = GRID.dp,
    defaultHeight = GRID.dp,
    viewportWidth = GRID,
    viewportHeight = GRID,
)

private val RanksMark: ImageVector = podiumBuilder("RanksMark.Outlined")
    .addPath(
        pathData = PodiumOutlined,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    .build()

private val RanksMarkFilled: ImageVector = podiumBuilder("RanksMark.Filled")
    .addPath(pathData = PodiumFilled, fill = SolidColor(Color.Black))
    .build()

/** Ranks tab, unselected: the outlined podium. */
val PuzzleIcons.Ranks: ImageVector get() = RanksMark

/** Ranks tab, selected: three rounded solid podium bars. */
val PuzzleIcons.RanksFilled: ImageVector get() = RanksMarkFilled
