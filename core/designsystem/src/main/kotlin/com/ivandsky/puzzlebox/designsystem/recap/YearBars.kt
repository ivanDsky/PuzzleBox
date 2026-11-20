package com.ivandsky.puzzlebox.designsystem.recap

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A year's twelve [months] as bars [gap] apart (round-capped unless [corner] is set), tallest as high as the box; a month with no solve keeps a hairline. */
@Composable
fun YearBars(months: List<Int>, color: Color, modifier: Modifier = Modifier, gap: Dp = BarGap, corner: Dp? = null) {
    Canvas(modifier) {
        val count = months.size.coerceAtLeast(1)
        val gap = gap.toPx()
        val barWidth = (size.width - gap * (count - 1)) / count
        val tallest = months.maxOrNull()?.coerceAtLeast(1) ?: 1
        months.forEachIndexed { i, value ->
            val height = (size.height * value / tallest).coerceAtLeast(MIN_BAR.toPx())
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barWidth + gap), size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(corner?.toPx() ?: (barWidth / 2)),
            )
        }
    }
}

private val MIN_BAR = 2.dp

private val BarGap = 1.5.dp
