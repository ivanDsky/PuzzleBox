package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Hub card status marks on the 24 grid (hub-card handoff §Text slots): ring = not
// started, ring + half disc = in progress, retry arrow = offline. Round caps/joins like
// BoxMark; the retry arrow strokes thinner (2.5) so its arrowhead does not clot at 14dp.
// Colour comes from the Icon tint.

private const val GRID = 24f
private const val RING_STROKE = 3f
private const val RETRY_STROKE = 2.5f

private val RingPath = addPathNodes("M12 3.5A8.5 8.5 0 1 1 12 20.5A8.5 8.5 0 1 1 12 3.5Z")
private val HalfDiscPath = addPathNodes("M12 7A5 5 0 0 1 12 17Z")
private val RetryPath = addPathNodes("M19 12A7 7 0 1 1 16.95 7.05M19 4.5V8H15.5")

private fun statusBuilder(name: String) = ImageVector.Builder(
    name = name,
    defaultWidth = GRID.dp,
    defaultHeight = GRID.dp,
    viewportWidth = GRID,
    viewportHeight = GRID,
)

private fun ImageVector.Builder.stroked(nodes: List<PathNode>, width: Float) =
    addPath(
        pathData = nodes,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )

internal val StatusRingMark: ImageVector = statusBuilder("StatusMark.Ring")
    .stroked(RingPath, RING_STROKE)
    .build()

internal val StatusHalfMark: ImageVector = statusBuilder("StatusMark.Half")
    .stroked(RingPath, RING_STROKE)
    .addPath(pathData = HalfDiscPath, fill = SolidColor(Color.Black))
    .build()

internal val RetryMark: ImageVector = statusBuilder("StatusMark.Retry")
    .stroked(RetryPath, RETRY_STROKE)
    .build()
