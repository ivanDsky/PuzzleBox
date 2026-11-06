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
// started, ring + half disc = in progress, retry arrow = offline; for past days (06-a
// §Day card) a phone = saved on this device, a cloud = needs a connection. Round
// caps/joins like BoxMark; the retry arrow and the outline marks stroke thinner (2.5,
// 2) so their detail does not clot at 14dp. Colour comes from the Icon tint.

private const val GRID = 24f
private const val RING_STROKE = 3f
private const val RETRY_STROKE = 2.5f
private const val OUTLINE_STROKE = 2f

private val RingPath = addPathNodes("M12 3.5A8.5 8.5 0 1 1 12 20.5A8.5 8.5 0 1 1 12 3.5Z")
private val HalfDiscPath = addPathNodes("M12 7A5 5 0 0 1 12 17Z")
private val RetryPath = addPathNodes("M19 12A7 7 0 1 1 16.95 7.05M19 4.5V8H15.5")
private val PhonePath = addPathNodes("M9 3H15A2 2 0 0 1 17 5V19A2 2 0 0 1 15 21H9A2 2 0 0 1 7 19V5A2 2 0 0 1 9 3ZM11 17.5H13")
private val CloudPath = addPathNodes("M7.5 18.5H17A3.75 3.75 0 0 0 17.6 11.05A5.75 5.75 0 0 0 6.6 11.6A3.5 3.5 0 0 0 7.5 18.5Z")

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

internal val SavedOnDeviceMark: ImageVector = statusBuilder("StatusMark.SavedOnDevice")
    .stroked(PhonePath, OUTLINE_STROKE)
    .build()

internal val NeedsConnectionMark: ImageVector = statusBuilder("StatusMark.NeedsConnection")
    .stroked(CloudPath, OUTLINE_STROKE)
    .build()
