package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// The four-petal set mark: a full set done, every puzzle of the day. Four filled petals on the 24 grid covering
// 3..21 with a 1dp gap, each a tile rounded 3.5 on its outer corners and 1.5 where it meets the others, so the
// meeting point opens to a small star. Unlike the Box mark's even tiles, it reads as one flower. Color comes
// from the Icon tint.

private const val GRID = 24f

private val Petals = addPathNodes(
    "M6.5 3H8A3.5 3.5 0 0 1 11.5 6.5V10A1.5 1.5 0 0 1 10 11.5H6.5A3.5 3.5 0 0 1 3 8V6.5A3.5 3.5 0 0 1 6.5 3Z" +
        "M16 3H17.5A3.5 3.5 0 0 1 21 6.5V8A3.5 3.5 0 0 1 17.5 11.5H14A1.5 1.5 0 0 1 12.5 10V6.5A3.5 3.5 0 0 1 16 3Z" +
        "M6.5 12.5H10A1.5 1.5 0 0 1 11.5 14V17.5A3.5 3.5 0 0 1 8 21H6.5A3.5 3.5 0 0 1 3 17.5V16A3.5 3.5 0 0 1 6.5 12.5Z" +
        "M14 12.5H17.5A3.5 3.5 0 0 1 21 16V17.5A3.5 3.5 0 0 1 17.5 21H16A3.5 3.5 0 0 1 12.5 17.5V14A1.5 1.5 0 0 1 14 12.5Z",
)

internal val SetMark: ImageVector = ImageVector.Builder(
    name = "SetMark",
    defaultWidth = GRID.dp,
    defaultHeight = GRID.dp,
    viewportWidth = GRID,
    viewportHeight = GRID,
)
    .addPath(pathData = Petals, fill = SolidColor(Color.Black))
    .build()
