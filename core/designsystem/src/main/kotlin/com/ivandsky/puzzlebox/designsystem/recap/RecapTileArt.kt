package com.ivandsky.puzzlebox.designsystem.recap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Stamp
import kotlinx.coroutines.delay

/** What a cover tile draws on its dark art plate: a game-colour mosaic, or a year's twelve months as bars. */
sealed interface RecapTileArt {
    /** [cells] colours in reading order, [MOSAIC_CELLS] of them (fewer leave [empty] cells). */
    class Mosaic(val cells: List<Color>, val empty: Color) : RecapTileArt

    /** One bar per month, [months] counts, in [color]. */
    class Bars(val months: List<Int>, val color: Color) : RecapTileArt
}

internal val ArtSize: Dp = 88.dp
private val ArtRadius = 16.dp
private val ArtPad = 7.dp
private val CellGap = 3.dp
private val CellRadius = 3.dp
private const val STAMP_STEP_MS = 42L

/** The art plate: [art] on [plate]; a mosaic square stamps in on a diagonal wave when [stamp]. */
@Composable
internal fun RecapArtPlate(art: RecapTileArt, plate: Color, stamp: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(ArtSize).background(plate, RoundedCornerShape(ArtRadius)).padding(ArtPad)) {
        when (art) {
            is RecapTileArt.Mosaic -> MosaicCells(art, stamp)
            is RecapTileArt.Bars -> YearBars(art.months, art.color, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun MosaicCells(art: RecapTileArt.Mosaic, stamp: Boolean) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CellGap)) {
        repeat(MOSAIC_SIDE) { row ->
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(CellGap)) {
                repeat(MOSAIC_SIDE) { col ->
                    val color = art.cells.getOrNull(row * MOSAIC_SIDE + col) ?: art.empty
                    var shown by remember { mutableStateOf(!stamp) }
                    LaunchedEffect(stamp) {
                        if (stamp) {
                            delay((row + col) * STAMP_STEP_MS)
                            shown = true
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Stamp(visible = shown) { Box(Modifier.fillMaxSize().background(color, RoundedCornerShape(CellRadius))) }
                    }
                }
            }
        }
    }
}

/** A year's twelve [months] as bars, tallest as high as the box; a month with no solve keeps a hairline. */
@Composable
internal fun YearBars(months: List<Int>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val count = months.size.coerceAtLeast(1)
        val gap = CellGap.toPx() / 2
        val barWidth = (size.width - gap * (count - 1)) / count
        val tallest = months.maxOrNull()?.coerceAtLeast(1) ?: 1
        months.forEachIndexed { i, value ->
            val height = (size.height * value / tallest).coerceAtLeast(MIN_BAR.toPx())
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barWidth + gap), size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

private val MIN_BAR = 2.dp
