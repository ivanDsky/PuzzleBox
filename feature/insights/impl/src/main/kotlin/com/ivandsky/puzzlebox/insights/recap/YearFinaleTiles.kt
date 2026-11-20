package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId

/**
 * Twelve months as four columns of three, one tile each. A wide pane keeps the grid at the reference's proportions
 * (tiles about half as wide as tall), centred in its space and capped in height, so a larger tablet gets a larger
 * grid and not taller tiles; it also names each month's best game.
 */
@Composable
internal fun Tiles(months: List<RecapCard.YearFinale.Tile>, plate: Color, wide: Boolean, gameName: (GameId) -> String, modifier: Modifier) {
    val grid: @Composable (Modifier) -> Unit = { gridModifier ->
        Column(gridModifier.testTag("recap.finale.tiles"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            months.chunked(TileColumns).forEachIndexed { row, rowTiles ->
                Row(
                    if (wide) Modifier.weight(1f) else Modifier,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    rowTiles.forEachIndexed { column, tile ->
                        Tile(tile, row * TileColumns + column, plate, wide, gameName)
                    }
                }
            }
        }
    }
    if (wide) {
        Box(modifier, contentAlignment = Alignment.Center) {
            grid(Modifier.heightIn(max = MaxGridHeight).aspectRatio(WideGridAspect, matchHeightConstraintsFirst = true))
        }
    } else {
        grid(modifier)
    }
}

@Composable
private fun RowScope.Tile(tile: RecapCard.YearFinale.Tile, index: Int, plate: Color, wide: Boolean, gameName: (GameId) -> String) {
    val progress = rememberDeckSpringIn(RecapMotion.YearTileStart + index * RecapMotion.YearTileStep)
    val density = LocalDensity.current
    val shape = MaterialTheme.shapes.medium
    val empty = tile.solved == 0
    Column(
        Modifier
            .weight(1f)
            .then(if (wide) Modifier.fillMaxHeight() else Modifier.aspectRatio(PhoneTileAspect))
            .graphicsLayer {
                val away = 1f - progress
                translationX = away * with(density) { RecapMotion.YearTileFromX.toPx() }
                translationY = away * with(density) { RecapMotion.YearTileFromY.toPx() }
                rotationZ = away * RecapMotion.YearTileTilt
                val scale = 1f + away * (RecapMotion.YearTileFromScale - 1f)
                scaleX = scale
                scaleY = scale
                alpha = progress.coerceIn(0f, 1f)
            }
            .background(plate, shape)
            .padding(Spacing.sm)
            .semantics(mergeDescendants = true) {}
            .testTag("recap.finale.tile.${tile.month.monthValue}"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(tile.month.shortName(), style = MaterialTheme.typography.labelMedium.copy(fontSize = if (wide) 14.sp else 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp))
        Text(
            text = if (empty) "–" else tile.solved.toString(),
            style = MaterialTheme.typography.headlineMedium.tabular().copy(fontSize = if (wide) 28.sp else 18.sp, fontWeight = FontWeight.Bold),
            color = LocalContentColor.current.copy(alpha = if (empty) MutedAlpha else 1f),
        )
        if (wide && tile.game != null) {
            Box(Modifier.padding(top = Spacing.xs).size(DotSize).background(LocalGameBrands.current(tile.game).accent.colors().accent, CircleShape))
            Text(gameName(tile.game), style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp), maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

private val DotSize = 10.dp
private const val TileColumns = 4
private const val MutedAlpha = 0.5f

/** The reference's wide grid: 376 x 550dp, tiles 88 x 178dp. */
private const val WideGridAspect = 376f / 550f
private const val PhoneTileAspect = 76f / 66f
private val MaxGridHeight = 640.dp
