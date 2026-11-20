package com.ivandsky.puzzlebox.designsystem.recap

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Stamp
import kotlinx.coroutines.delay

private val ArtRadius = 16.dp
private val ArtPad = 7.dp
private val CellGap = 3.dp
private val CellRadius = 3.dp
private const val STAMP_STEP_MS = 42L

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

@Composable
private fun MosaicCells(art: RecapTileArt.Mosaic, stamp: Boolean, gap: Dp, radius: Dp) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
        repeat(MOSAIC_SIDE) { row ->
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
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
                        Stamp(visible = shown) { Box(Modifier.fillMaxSize().background(color, RoundedCornerShape(radius))) }
                    }
                }
            }
        }
    }
}
