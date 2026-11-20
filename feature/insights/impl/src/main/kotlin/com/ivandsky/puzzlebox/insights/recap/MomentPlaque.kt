package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
 * is peeled. It keeps the height of two label lines, so a long label never moves what sits below it. With nothing peeled
 * yet (the stickers are still being stamped) it invites a tap.
 */
@Composable
internal fun MomentPlaque(moment: EarnedMoment?, gameName: (GameId) -> String, modifier: Modifier = Modifier) {
    val ink = PaletteEntry.Moments.ink
    val style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    val twoLines = with(LocalDensity.current) { (style.lineHeight * 2).toDp() }
    MorphCell(
        value = moment,
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(PaletteEntry.Moments.plate)
            .heightIn(min = twoLines + Spacing.md * 2)
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

/** The few characters a sticker carries on its face: "PB", the time, "SET" or "7d". */
internal fun EarnedMoment.face(): String = when (kind) {
    EarnedMoment.Kind.PersonalBest -> "PB"
    EarnedMoment.Kind.FirstSubX -> formatDuration(thresholdMs ?: 0L)
    EarnedMoment.Kind.FullSet -> "SET"
    EarnedMoment.Kind.Streak -> "${streakDays}d"
}
