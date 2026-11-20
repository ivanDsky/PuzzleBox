package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.RollingDigits
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.Stamp
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.entranceSettled
import com.ivandsky.puzzlebox.designsystem.field
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.designsystem.plate
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * The year on one poster: the total over a grid of twelve months (a month with no counted solve is an en dash), then the
 * best month, the fastest solve and the most played game as chips, and a Share button that moves on to the Share page.
 * The tiles throw themselves in one after another, the chips stamp in after them and, once, confetti falls (none under
 * reduced motion). [displayName] is the name the shared image carries; blank or null leaves it out of the note.
 */
@Composable
internal fun YearFinalePage(
    card: RecapCard.YearFinale,
    periodLabel: String,
    gameName: (GameId) -> String,
    displayName: String?,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fade = rememberReducedFade()
    val palette = PaletteEntry.YearFinale
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer { alpha = fade }
            .background(palette.field)
            .testTag("recap.finale"),
    ) {
        val wide = maxWidth >= WideWidth
        OnInk(palette.ink) {
            val tiles: @Composable (Modifier) -> Unit = { Tiles(card.months, palette.plate, wide, gameName, it) }
            val chips: @Composable () -> Unit = { Chips(card, palette.plate, gameName) }
            val actions: @Composable ColumnScope.() -> Unit = {
                chips()
                ShareButton(palette, onShare)
                Note(displayName)
            }
            Column(Modifier.fillMaxSize().padding(horizontal = Spacing.xl).padding(top = Spacing.xl, bottom = Spacing.lg)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Eyebrow(periodLabel)
                    Eyebrow("Year finale")
                }
                if (wide) {
                    Row(Modifier.weight(1f).padding(vertical = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                            Hero(card.total, card.importedDays)
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), content = actions)
                        }
                        tiles(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Spacer(Modifier.height(Spacing.lg))
                    Hero(card.total, card.importedDays)
                    Spacer(Modifier.height(Spacing.lg))
                    tiles(Modifier)
                    Spacer(Modifier.weight(1f))
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), content = actions)
                }
            }
        }
        Confetti(palette.ink, palette.plate)
    }
}

@Composable
private fun Hero(total: Int, importedDays: Int?) {
    RollingDigits(
        value = total.toLong(),
        modifier = Modifier.testTag("recap.finale.hero"),
        style = MaterialTheme.typography.displayLarge.tabular().copy(fontSize = HeroFontSize, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        contentDescription = "$total ${total.puzzles()} solved",
    )
    Text(
        text = "${total.puzzles()} solved",
        modifier = Modifier.clearAndSetSemantics { },
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
    )
    importedDays?.let {
        Text(importedDaysLine(it), modifier = Modifier.testTag("recap.finale.imported"), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Twelve months as four columns of three: one tile each; a wide pane makes them tall and names each month's quickest game. */
@Composable
private fun Tiles(months: List<RecapCard.YearFinale.Tile>, plate: Color, wide: Boolean, gameName: (GameId) -> String, modifier: Modifier) {
    Column(modifier.testTag("recap.finale.tiles"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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

@Composable
private fun RowScope.Tile(tile: RecapCard.YearFinale.Tile, index: Int, plate: Color, wide: Boolean, gameName: (GameId) -> String) {
    val progress = rememberDeckSpringIn(RecapMotion.YearTileStart + index * RecapMotion.YearTileStep)
    val density = LocalDensity.current
    val shape = MaterialTheme.shapes.medium
    val empty = tile.solved == 0
    Column(
        Modifier
            .weight(1f)
            .then(if (wide) Modifier.fillMaxHeight() else Modifier.aspectRatio(1f))
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
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(tile.month.shortName(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
        if (wide && tile.game != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Box(Modifier.size(DotSize).background(LocalGameAccent.current(tile.game).colors().accent, CircleShape))
                Text(gameName(tile.game), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
        Text(
            text = if (empty) "–" else tile.solved.toString(),
            style = MaterialTheme.typography.headlineMedium.tabular().copy(fontWeight = FontWeight.Bold),
            color = LocalContentColor.current.copy(alpha = if (empty) MutedAlpha else 1f),
        )
    }
}

/** The three bests, as equal chips that stamp in one after another. */
@Composable
private fun Chips(card: RecapCard.YearFinale, plate: Color, gameName: (GameId) -> String) {
    val chips = listOf(
        Triple("best", "Best month" to card.bestMonth.monthName(), "${card.bestMonthSolved} ${card.bestMonthSolved.puzzles()}"),
        Triple("fastest", "Fastest solve" to formatDuration(card.fastestTimeMs), "${gameName(card.fastestGame)} · ${card.fastestMonth.shortName(upper = false)}"),
        Triple("most", "Most played" to gameName(card.mostPlayedGame), "${card.mostPlayedSolved} ${card.mostPlayedSolved.puzzles()}"),
    )
    val lastTile = RecapMotion.YearTileStart + (card.months.size - 1) * RecapMotion.YearTileStep
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        chips.forEachIndexed { index, (tag, top, sub) ->
            val instant = rememberReducedMotion() || entranceSettled()
            var shown by remember { mutableStateOf(instant) }
            LaunchedEffect(instant) {
                if (!instant) {
                    delay((lastTile + index * RecapMotion.YearChipStep).toLong())
                    shown = true
                }
            }
            Box(Modifier.weight(1f)) {
                Stamp(visible = shown) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .height(ChipHeight)
                            .background(plate, MaterialTheme.shapes.medium)
                            .padding(Spacing.sm)
                            .semantics(mergeDescendants = true) {}
                            .testTag("recap.finale.chip.$tag"),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(top.first, style = MaterialTheme.typography.labelSmall)
                        Text(top.second, style = MaterialTheme.typography.titleSmall.tabular().copy(fontWeight = FontWeight.Bold), maxLines = 1, softWrap = false)
                        Text(sub, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareButton(palette: PaletteEntry, onShare: () -> Unit) {
    Button(
        onClick = onShare,
        modifier = Modifier.fillMaxWidth().height(ButtonHeight).testTag("recap.finale.share"),
        colors = ButtonDefaults.buttonColors(containerColor = palette.ink, contentColor = palette.field),
    ) { Text("Share") }
}

@Composable
private fun Note(displayName: String?) {
    val name = displayName?.takeIf { it.isNotBlank() }
    Text(
        text = if (name == null) "The image shows your numbers only." else "The image shows your numbers and your name, $name, only.",
        modifier = Modifier.testTag("recap.finale.note"),
        style = MaterialTheme.typography.bodySmall,
    )
}

internal fun YearMonth.monthName(): String = month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())

internal fun YearMonth.shortName(upper: Boolean = true): String =
    month.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault()).let { if (upper) it.uppercase() else it }

private val HeroFontSize = 88.sp
private val WideWidth = 600.dp
private val ChipHeight = 96.dp
private val ButtonHeight: Dp = 48.dp
private val DotSize = 10.dp
private const val TileColumns = 4

private const val MutedAlpha = 0.5f
