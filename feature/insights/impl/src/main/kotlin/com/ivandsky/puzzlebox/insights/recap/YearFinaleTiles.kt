package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId

/**
 * Twelve months as four columns of three, one tile each. A wide pane keeps the grid at the reference's proportions
 * (tiles about half as wide as tall), centred in its space and capped in height, so a larger tablet gets a larger
 * grid and not taller tiles; it also names each month's best game. A narrow pane lets the grid take the height the page
 * has left, up to that same tile proportion, and shows the glyph alone; the tile's semantics name the game.
 */
@Composable
internal fun Tiles(months: List<RecapCard.YearFinale.Tile>, plate: Color, wide: Boolean, gameName: (GameId) -> String, modifier: Modifier) {
    val grid: @Composable (Modifier, GameGlyphSize?) -> Unit = { gridModifier, glyphSize ->
        Column(gridModifier.testTag("recap.finale.tiles"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            months.chunked(TileColumns).forEachIndexed { row, rowTiles ->
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    rowTiles.forEachIndexed { column, tile ->
                        Tile(tile, row * TileColumns + column, plate, wide, glyphSize, gameName)
                    }
                }
            }
        }
    }
    if (wide) {
        Box(modifier, contentAlignment = Alignment.Center) {
            grid(Modifier.heightIn(max = MaxGridHeight).aspectRatio(WideGridAspect, matchHeightConstraintsFirst = true), GameGlyphSize.Tile)
        }
    } else {
        BoxWithConstraints(modifier) {
            val tileWidth = (maxWidth - Spacing.sm * (TileColumns - 1)) / TileColumns
            val gaps = Spacing.sm * (TileRows - 1)
            val maxGrid = tileWidth * MaxTileHeightPerWidth * TileRows + gaps
            val gridHeight = minOf(maxHeight, maxGrid)
            val textRoom = with(LocalDensity.current) { (PhoneMonthLine.value + PhoneCountLine.value).sp.toDp() }
            val room = (gridHeight - gaps) / TileRows - PhoneTilePadding * 2 - textRoom
            grid(Modifier.fillMaxWidth().heightIn(min = MinPhoneTileHeight * TileRows + gaps, max = maxGrid), glyphSizeFor(room))
        }
    }
}

@Composable
private fun RowScope.Tile(tile: RecapCard.YearFinale.Tile, index: Int, plate: Color, wide: Boolean, glyphSize: GameGlyphSize?, gameName: (GameId) -> String) {
    val progress = rememberDeckSpringIn(RecapMotion.YearTileStart + index * RecapMotion.YearTileStep)
    val density = LocalDensity.current
    val shape = MaterialTheme.shapes.medium
    val empty = tile.solved == 0
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
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
            .padding(horizontal = Spacing.sm, vertical = if (wide) Spacing.sm else PhoneTilePadding)
            .semantics(mergeDescendants = true) { if (!wide && tile.game != null) contentDescription = gameName(tile.game) }
            .testTag("recap.finale.tile.${tile.month.monthValue}"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(tile.month.shortName(), style = MaterialTheme.typography.labelMedium.copy(fontSize = if (wide) 14.sp else 13.sp, lineHeight = if (wide) 16.sp else PhoneMonthLine, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp))
        Text(
            text = if (empty) "–" else tile.solved.toString(),
            style = MaterialTheme.typography.headlineMedium.tabular().copy(fontSize = if (wide) 28.sp else 18.sp, lineHeight = if (wide) 36.sp else PhoneCountLine, fontWeight = FontWeight.Bold),
            color = LocalContentColor.current.copy(alpha = if (empty) MutedAlpha else 1f),
        )
        if (glyphSize != null && tile.game != null) {
            GameGlyph(LocalGameBrands.current(tile.game), glyphSize, if (wide) Modifier.padding(top = Spacing.xs) else Modifier)
            if (wide) {
                Text(gameName(tile.game), style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp), maxLines = 2, textAlign = TextAlign.Center)
            }
        }
    }
}

private const val TileColumns = 4
private const val MutedAlpha = 0.5f

/** The reference's wide grid: 376 x 550dp, tiles 88 x 178dp. */
private const val WideGridAspect = 376f / 550f
private const val TileRows = 3

/** A phone tile aims for room to show a chip-sized glyph, and never grows past the wide tiles' 88 x 178dp proportion. */
private val MinPhoneTileHeight = 96.dp
private const val MaxTileHeightPerWidth = 178f / 88f

private val PhoneTilePadding = Spacing.xs
private val PhoneMonthLine = 16.sp
private val PhoneCountLine = 22.sp

/** The biggest glyph that fits in the [room] a phone tile has left under its month and count; none when even the badge would clip. */
private fun glyphSizeFor(room: Dp): GameGlyphSize? = when {
    room >= GameGlyphSize.Tile.box -> GameGlyphSize.Tile
    room >= GameGlyphSize.Chip.box -> GameGlyphSize.Chip
    room >= GameGlyphSize.Badge.box -> GameGlyphSize.Badge
    else -> null
}

private val MaxGridHeight = 640.dp
