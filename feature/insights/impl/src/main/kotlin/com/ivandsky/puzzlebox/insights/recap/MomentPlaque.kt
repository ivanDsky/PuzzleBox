package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.MorphCell
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.designsystem.plate
import com.ivandsky.puzzlebox.insights.domain.recap.EarnedMoment
import com.ivandsky.puzzlebox.model.GameId

/**
 * The plaque under the sticker sheet: the peeled [moment]'s label and puzzle day, morphing in place when another sticker
 * is peeled. It keeps the height of the tallest of [all] the labels at this width (one line if none wraps, two if one does),
 * so a peel never moves what sits below it. With nothing peeled yet (the stickers are still being stamped) it invites a tap.
 */
@Composable
internal fun MomentPlaque(moment: EarnedMoment?, all: List<EarnedMoment>, gameName: (GameId) -> String, modifier: Modifier = Modifier) {
    val ink = PaletteEntry.Moments.ink
    val style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    val dayStyle = MaterialTheme.typography.bodyMedium
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val lines = all.maxOfOrNull { density.labelLines(it, gameName, style, dayStyle, measurer, maxWidth) } ?: 1
        val tallest = with(density) { (style.lineHeight * lines).toDp() }
        PlaqueCell(moment, gameName, style, ink, Modifier.fillMaxWidth().heightIn(min = tallest + Spacing.md * 2))
    }
}

/** How many lines (one or two) [moment]'s label takes on a plaque [width] wide, beside its day. */
private fun Density.labelLines(
    moment: EarnedMoment,
    gameName: (GameId) -> String,
    style: TextStyle,
    dayStyle: TextStyle,
    measurer: TextMeasurer,
    width: Dp,
): Int {
    val day = measurer.measure(moment.day.shortLabel(), dayStyle, maxLines = 1).size.width
    val room = (width - Spacing.lg * 2 - Spacing.md).roundToPx() - day
    return measurer.measure(moment.label(gameName), style, maxLines = 2, constraints = Constraints(maxWidth = room.coerceAtLeast(0))).lineCount
}

@Composable
private fun PlaqueCell(moment: EarnedMoment?, gameName: (GameId) -> String, style: TextStyle, ink: Color, modifier: Modifier) {
    MorphCell(
        value = moment,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(PaletteEntry.Moments.plate)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag("recap.moments.plaque"),
    ) { shown ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                text = shown?.label(gameName) ?: "Tap a sticker to read it",
                modifier = Modifier.weight(1f),
                style = style,
                color = ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (shown != null) Text(shown.day.shortLabel(), style = MaterialTheme.typography.bodyMedium, color = ink, maxLines = 1)
        }
    }
}

/** What a Moment is called on its plaque and to a screen reader: "Personal best in Queens", "First under 1:00 in Zip", "Full set done", "7-day streak". */
internal fun EarnedMoment.label(gameName: (GameId) -> String): String {
    val game = game?.let { " in ${gameName(it)}" }.orEmpty()
    return when (kind) {
        EarnedMoment.Kind.PersonalBest -> "Personal best$game"
        EarnedMoment.Kind.FirstSubX -> "First under ${formatDuration(thresholdMs ?: 0L)}$game"
        EarnedMoment.Kind.FullSet -> "Full set done"
        EarnedMoment.Kind.Streak -> "$streakDays-day streak"
    }
}
