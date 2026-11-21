package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// What a sticker draws around itself: the dashed slot it is stamped on and the paper corner turned up when it is peeled.

/** The dashed slot in [shape], in [color] at [alpha]: under a Moments sticker, or alone on a month board with no stamps. */
internal fun DrawScope.slotOutline(shape: Shape, color: Color, alpha: Float = SlotAlpha) {
    val corner = if (shape == CircleShape) size.minDimension / 2 else size.minDimension * SlotCorner
    drawRoundRect(
        color = color.copy(alpha = alpha),
        cornerRadius = CornerRadius(corner),
        style = Stroke(SlotStroke.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(SlotDash.toPx(), SlotDash.toPx()))),
        size = Size(size.width, size.height),
        topLeft = Offset.Zero,
    )
}

/** The paper corner turned up at the bottom-end of a peeled sticker, past its edge, [progress] 0 to 1 of the way up. */
internal fun ContentDrawScope.drawCurl(progress: Float, color: Color) {
    val edge = size.minDimension * CurlSize * progress
    val right = size.width + CurlOut.toPx()
    val bottom = size.height + CurlOut.toPx()
    drawPath(
        Path().apply {
            moveTo(right, bottom - edge)
            lineTo(right - edge, bottom)
            lineTo(right, bottom)
            close()
        },
        color,
    )
}

private val SlotStroke = 2.dp
private val SlotDash = 6.dp
private const val SlotAlpha = 0.5f
private const val SlotCorner = 0.3f
private const val CurlSize = 0.44f
private val CurlOut = 3.dp
