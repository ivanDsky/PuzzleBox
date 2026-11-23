package com.ivandsky.puzzlebox.designsystem.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val ArtRadius = 16.dp
private val ArtPad = 7.dp
private val CellGap = 3.dp
private val CellRadius = 3.dp

/**
 * The art plate: [art] on [plate], [size] square (the plate's corners, padding and cell gaps scale with it); a mosaic
 * square stamps in on a diagonal wave when [stamp].
 */
@Composable
fun RecapArtPlate(art: RecapTileArt, plate: Color, stamp: Boolean, modifier: Modifier = Modifier, size: Dp = ArtSize) {
    val scale = size / ArtSize
    Box(modifier.size(size).background(plate, RoundedCornerShape(ArtRadius * scale)).padding(ArtPad * scale)) {
        when (art) {
            is RecapTileArt.Mosaic -> MosaicCells(art, stamp, CellGap * scale, CellRadius * scale)
            is RecapTileArt.Bars -> YearBars(art.months, art.color, Modifier.fillMaxSize())
        }
    }
}

/**
 * The mosaic as one drawing: its 25 cells are rounded rects painted in one draw pass, each from its own share of a single
 * wave clock ([mosaicCellProgress]). A tile in a scrolling grid composes and disposes this as one node with one
 * animation, not 25 transitions with an effect each.
 */
@Composable
private fun MosaicCells(art: RecapTileArt.Mosaic, stamp: Boolean, gap: Dp, radius: Dp) {
    val colors = remember(art.cells, art.empty) { List(MOSAIC_CELLS) { art.cells.getOrNull(it) ?: art.empty } }
    val wave = remember { Animatable(if (stamp) 0f else 1f) }
    LaunchedEffect(stamp) {
        if (stamp) wave.animateTo(1f, tween(MOSAIC_WAVE_MS, easing = LinearEasing)) else wave.snapTo(1f)
    }
    Spacer(
        Modifier.fillMaxSize().drawBehind {
            val gapPx = gap.toPx()
            val cell = (size.width - gapPx * (MOSAIC_SIDE - 1)) / MOSAIC_SIDE
            val cellH = (size.height - gapPx * (MOSAIC_SIDE - 1)) / MOSAIC_SIDE
            val corner = CornerRadius(radius.toPx())
            val settled = wave.value >= 1f
            for (row in 0 until MOSAIC_SIDE) for (col in 0 until MOSAIC_SIDE) {
                val progress = if (settled) 1f else mosaicCellProgress(wave.value, row, col)
                if (progress <= 0f) continue
                val scale = mosaicStampScale(progress)
                val w = cell * scale
                val h = cellH * scale
                val cx = col * (cell + gapPx) + cell / 2f
                val cy = row * (cellH + gapPx) + cellH / 2f
                drawRoundRect(
                    colors[row * MOSAIC_SIDE + col],
                    topLeft = Offset(cx - w / 2f, cy - h / 2f),
                    size = Size(w, h),
                    cornerRadius = corner,
                    alpha = mosaicStampAlpha(progress),
                )
            }
        },
    )
}
