package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.RecapCardColors
import com.ivandsky.puzzlebox.designsystem.entranceSettled
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.MonthHighlight
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId

/** A month card's measures: a phone, a short phone ([compact]), or a wide tablet pane in two columns ([roomy]). */
internal class MonthCardScale(val roomy: Boolean, val compact: Boolean) {
    val tile: Dp get() = if (roomy) 56.dp else 48.dp
    val tileRadius: Dp get() = if (roomy) 16.dp else 14.dp
    val tileGlyph: Dp get() = if (roomy) 30.dp else 26.dp
    val rowMin: Dp get() = when {
        roomy && compact -> 56.dp
        roomy -> 68.dp
        compact -> 54.dp
        else -> 60.dp
    }
    val boardFloor: Dp get() = if (compact) 150.dp else 168.dp

    val title: TextStyle @Composable get() = type(if (roomy) 30 else 22, if (roomy) 36 else 26, FontWeight.Bold)
    val line: TextStyle @Composable get() = type(if (roomy) 16 else 14, if (roomy) 22 else 20, FontWeight.Medium)
    val label: TextStyle @Composable get() = type(if (roomy) 20 else 16, if (roomy) 28 else 22, FontWeight.SemiBold)
    val sub: TextStyle @Composable get() = type(if (roomy) 14 else 13, if (roomy) 20 else 18, FontWeight.Medium)
    val value: TextStyle @Composable get() = type(if (roomy) 26 else 22, if (roomy) 30 else 26, FontWeight.Bold).tabular()
}

@Composable
private fun type(size: Int, line: Int, weight: FontWeight): TextStyle =
    MaterialTheme.typography.bodyLarge.copy(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

/**
 * The month's lead: an ink tile with its glyph in the card's field color, the headline, and the detail with the day
 * it happened right-aligned on the detail's baseline (no day for a highlight that took the whole month).
 */
@Composable
internal fun MonthHighlightRow(
    highlight: MonthHighlight,
    colors: RecapCardColors,
    gameName: (GameId) -> String,
    scale: MonthCardScale,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("recap.month.highlight"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(scale.tile).background(colors.ink, RoundedCornerShape(scale.tileRadius)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(highlight.glyph, contentDescription = null, tint = colors.field, modifier = Modifier.size(scale.tileGlyph))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(highlight.title(gameName), style = scale.title)
            val detail = highlight.detail(gameName)
            val day = highlight.day
            if (detail.isNotEmpty() || day != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(detail, Modifier.weight(1f).alignByBaseline(), style = scale.line)
                    if (day != null) {
                        Text(
                            day.shortLabel(),
                            Modifier.alignByBaseline().testTag("recap.month.highlight.day"),
                            style = scale.line.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

/** "Also: ◆ 7-day streak": the second thing the month did, under its lead. */
@Composable
internal fun MonthAlsoLine(also: MonthHighlight, gameName: (GameId) -> String, scale: MonthCardScale, modifier: Modifier = Modifier) {
    val style = scale.line.copy(fontWeight = FontWeight.SemiBold)
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("recap.month.also"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Also:", Modifier.alignByBaseline(), style = style)
        Icon(also.glyph, contentDescription = null, modifier = Modifier.size(16.dp).align(Alignment.CenterVertically))
        Text(also.title(gameName), Modifier.weight(1f).alignByBaseline(), style = style)
    }
}

/** The month's plate: its quickest solve, and its best against the player's own average, each with its game. */
@Composable
internal fun MonthPlateRows(
    card: RecapCard.Month,
    colors: RecapCardColors,
    gameName: (GameId) -> String,
    scale: MonthCardScale,
    modifier: Modifier = Modifier,
    rise: @Composable (Int) -> Modifier = { Modifier },
) {
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(colors.plate).testTag("recap.month.facts"),
    ) {
        PlateRow("recap.month.fastest", "Fastest solve", card.fastestGame, gameName(card.fastestGame), formatDuration(card.fastestTimeMs), scale, rise(0))
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = colors.ink.copy(alpha = DividerAlpha))
        PlateRow(
            "recap.month.best",
            "Best vs average",
            card.bestGame,
            "${gameName(card.bestGame)} · ${underAverageLine(card.underAveragePercent)}",
            formatDuration(card.bestTimeMs),
            scale,
            rise(1),
        )
    }
}

@Composable
private fun PlateRow(tag: String, label: String, game: GameId, sub: String, value: String, scale: MonthCardScale, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = scale.rowMin)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f).alignByBaseline(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, style = scale.label)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(LocalGameBrands.current(game).glyph, contentDescription = null, tint = LocalContentColor.current, modifier = Modifier.size(14.dp))
                Text(sub, style = scale.sub)
            }
        }
        Text(value, Modifier.alignByBaseline(), style = scale.value, maxLines = 1, softWrap = false)
    }
}

/**
 * A block of a month card rising in, [index] in its order: 8dp up and faded in over 240ms, [CardBeat] after the card is
 * dealt plus the enter stagger (capped). Reduced motion and a preview show it in place.
 */
@Composable
internal fun riseIn(index: Int): Modifier {
    val settled = rememberReducedMotion() || entranceSettled()
    val progress = remember { Animatable(if (settled) 1f else 0f) }
    LaunchedEffect(progress, settled) {
        if (settled) {
            progress.snapTo(1f)
        } else {
            val delay = CardBeat + minOf(index, MotionTokens.EnterStaggerCap) * MotionTokens.EnterStagger
            progress.animateTo(1f, tween(MotionTokens.EnterDuration, delay, MotionTokens.EasingEmphasizedDecelerate))
        }
    }
    return Modifier.graphicsLayer { rise(progress.value) }
}

/** "31% under your average"; a month that only matched it says so. */
internal fun underAverageLine(percent: Int): String = if (percent > 0) "$percent% under your average" else "Matched your average"

/** A month card's beat: its blocks start this long after it is dealt. */
private const val CardBeat = 180
private const val DividerAlpha = 0.16f
