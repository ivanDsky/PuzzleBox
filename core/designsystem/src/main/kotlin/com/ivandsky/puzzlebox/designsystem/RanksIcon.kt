package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// The Ranks tab's mark: a three-step podium on the 24 grid, first place in the middle. Not
// the Trophy, which marks a competition. Outlined with its steps divided when unselected,
// the same silhouette solid when selected; both stroke 2dp round over 4..20 like BoxMark, so
// the swap never shifts the silhouette. Extensions rather than members because PuzzleIcons.kt
// sits near the file-length cap; call sites still read `PuzzleIcons.Ranks`. Colour comes from
// the Icon tint.

private const val GRID = 24f
private const val STROKE = 2f
private const val PODIUM = "M4 20V12H9.33V4H14.67V9H20V20Z"

private val PodiumOutlined = addPathNodes(PODIUM + "M9.33 12V20M14.67 9V20")
private val PodiumFilled = addPathNodes(PODIUM)

private fun podium(name: String, nodes: List<PathNode>, filled: Boolean): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = GRID.dp,
        defaultHeight = GRID.dp,
        viewportWidth = GRID,
        viewportHeight = GRID,
    ).addPath(
        pathData = nodes,
        fill = SolidColor(Color.Black).takeIf { filled },
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

private val RanksMark = podium("RanksMark.Outlined", PodiumOutlined, filled = false)
private val RanksMarkFilled = podium("RanksMark.Filled", PodiumFilled, filled = true)

/** Ranks tab, unselected: the outlined podium. */
val PuzzleIcons.Ranks: ImageVector get() = RanksMark

/** Ranks tab, selected: the solid podium. */
val PuzzleIcons.RanksFilled: ImageVector get() = RanksMarkFilled
