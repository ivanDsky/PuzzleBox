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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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
        val scale = if (wide) minOf(maxWidth.value / RefCardWidth, maxHeight.value / RefCardHeight).coerceIn(1f, MaxScale) else 1f
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            OnInk(palette.ink) {
                val tiles: @Composable (Modifier) -> Unit = { Tiles(card.months, palette.plate, wide, gameName, it) }
                val chips: @Composable () -> Unit = { Chips(card, palette.plate, wide, gameName) }
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
                            Column(Modifier.weight(1f).fillMaxHeight()) {
                                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                    Column { Hero(card.total, card.importedDays, WideHeroFontSize, wide = true) }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), content = actions)
                            }
                            tiles(Modifier.weight(1f).fillMaxHeight())
                        }
                    } else {
                        Spacer(Modifier.height(Spacing.lg))
                        Hero(card.total, card.importedDays, HeroFontSize, wide = false)
                        Spacer(Modifier.height(Spacing.lg))
                        tiles(Modifier)
                        Spacer(Modifier.weight(1f))
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), content = actions)
                    }
                }
            }
        }
        Confetti(palette.ink, palette.plate)
    }
}

@Composable
private fun Hero(total: Int, importedDays: Int?, fontSize: TextUnit, wide: Boolean) {
    RollingDigits(
        value = total.toLong(),
        modifier = Modifier.testTag("recap.finale.hero"),
        style = MaterialTheme.typography.displayLarge.tabular().copy(fontSize = fontSize, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        contentDescription = "$total ${total.puzzles()} solved",
    )
    Text(
        text = "${total.puzzles()} solved",
        modifier = Modifier.clearAndSetSemantics { },
        style = MaterialTheme.typography.headlineSmall.copy(fontSize = if (wide) 32.sp else 28.sp, fontWeight = FontWeight.Bold),
    )
    importedDays?.let {
        Text(importedDaysLine(it), modifier = Modifier.testTag("recap.finale.imported"), style = MaterialTheme.typography.bodyMedium)
    }
}

/** The three bests, as equal chips that stamp in one after another. */
@Composable
private fun Chips(card: RecapCard.YearFinale, plate: Color, wide: Boolean, gameName: (GameId) -> String) {
    val chipLabelSize = if (wide) 14.sp else 13.sp
    val chipValueSize = if (wide) 22.sp else 20.sp
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
                            .heightIn(min = ChipHeight)
                            .background(plate, MaterialTheme.shapes.medium)
                            .padding(Spacing.sm)
                            .semantics(mergeDescendants = true) {}
                            .testTag("recap.finale.chip.$tag"),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(top.first, style = MaterialTheme.typography.labelSmall.copy(fontSize = chipLabelSize, letterSpacing = 0.sp))
                        Text(top.second, style = MaterialTheme.typography.titleSmall.tabular().copy(fontSize = chipValueSize, fontWeight = FontWeight.Bold, letterSpacing = 0.sp), maxLines = 1, softWrap = false)
                        Text(sub, style = MaterialTheme.typography.labelSmall.copy(fontSize = chipLabelSize, letterSpacing = 0.sp), maxLines = 1)
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

private val HeroFontSize = 96.sp
private val WideHeroFontSize = 108.sp
private val WideWidth = 600.dp
private val ChipHeight = 96.dp
private val ButtonHeight: Dp = 48.dp

/** The reference tablet card, 852 x 650dp of content room: a wide pane larger than this scales its page up to [MaxScale] times, so nothing stretches. */
private const val RefCardWidth = 852f
private const val RefCardHeight = 650f
private const val MaxScale = 1.6f
