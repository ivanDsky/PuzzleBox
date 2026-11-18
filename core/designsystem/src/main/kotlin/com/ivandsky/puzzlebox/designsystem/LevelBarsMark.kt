package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Difficulty marks on the 24 grid: four ascending bars, 3 wide with 2 gaps, bottoms on
// y=20, heights 6/10/14/18. A level fills as many bars as its rank (Easy 1 .. Expert 4);
// the rest stay as faint bars. At 14dp a 3-unit bar cannot hold a 2-unit outline stroke
// (the hole closes and the outlines touch), so the unfilled bars are the same shape at
// 30% alpha instead. Colour comes from the Icon tint, which keeps the alpha.

private const val GRID = 24f
private const val BAR_COUNT = 4
private const val BAR_WIDTH = 3
private const val BAR_GAP = 2
private const val BAR_BOTTOM = 20
private const val BAR_STEP = 4
private const val BAR_MIN_HEIGHT = 6
private const val BAR_RADIUS = 1
private const val FAINT_ALPHA = 0.3f
private const val LEFT = (24 - (BAR_COUNT * BAR_WIDTH + (BAR_COUNT - 1) * BAR_GAP)) / 2

private fun levelBuilder(name: String) = ImageVector.Builder(
    name = name,
    defaultWidth = GRID.dp,
    defaultHeight = GRID.dp,
    viewportWidth = GRID,
    viewportHeight = GRID,
)

// A bar with 1-unit rounded corners: left x, top y, bottom on BAR_BOTTOM.
private fun barPath(bar: Int): String {
    val x = LEFT + bar * (BAR_WIDTH + BAR_GAP)
    val top = BAR_BOTTOM - (BAR_MIN_HEIGHT + bar * BAR_STEP)
    val r = BAR_RADIUS
    val right = x + BAR_WIDTH
    return "M$x ${top + r}A$r $r 0 0 1 ${x + r} ${top}H${right - r}A$r $r 0 0 1 $right ${top + r}" +
        "V${BAR_BOTTOM - r}A$r $r 0 0 1 ${right - r} ${BAR_BOTTOM}H${x + r}A$r $r 0 0 1 $x ${BAR_BOTTOM - r}Z"
}

private fun levelMark(name: String, filled: Int): ImageVector {
    val builder = levelBuilder(name)
    repeat(BAR_COUNT) { bar ->
        builder.addPath(
            pathData = addPathNodes(barPath(bar)),
            fill = SolidColor(Color.Black),
            fillAlpha = if (bar < filled) 1f else FAINT_ALPHA,
        )
    }
    return builder.build()
}

internal val LevelEasyMark: ImageVector = levelMark("LevelBars.Easy", filled = 1)
internal val LevelMediumMark: ImageVector = levelMark("LevelBars.Medium", filled = 2)
internal val LevelHardMark: ImageVector = levelMark("LevelBars.Hard", filled = 3)
internal val LevelExpertMark: ImageVector = levelMark("LevelBars.Expert", filled = 4)
