package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize

/**
 * The recent days (A11): one row per day, newest first, each day's games as result chips
 * (glyph + solve time, or "–" when opened but not solved), and "See all" to the full
 * history ([onSeeAll]). In two-pane ([selectable]) a row selects that day for the detail
 * pane ([onDayClick]; [selectedDay] reads selected); single-pane rows are not clickable.
 */
@Composable
internal fun HomeRecentSection(
    days: List<HistoryDay>,
    onSeeAll: () -> Unit,
    selectedDay: String?,
    onDayClick: (String) -> Unit,
    selectable: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Recent",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            AppTextButton(text = "See all", onClick = onSeeAll)
        }
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        ) {
            if (days.isEmpty()) {
                EmptyState(
                    title = "No games yet",
                    message = "Finished puzzles and scores will appear here.",
                )
            } else {
                Column(Modifier.padding(Spacing.sm)) {
                    days.forEach { day ->
                        RecentRow(
                            day = day,
                            selected = selectable && day.isoDay == selectedDay,
                            onClick = if (selectable) {
                                { onDayClick(day.isoDay) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRow(
    day: HistoryDay,
    selected: Boolean,
    onClick: (() -> Unit)?,
) {
    val tagged = Modifier
        .fillMaxWidth()
        .testTag("home.history_row_${day.isoDay}")
    val color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    if (onClick != null) {
        Surface(
            selected = selected,
            onClick = onClick,
            modifier = tagged,
            shape = MaterialTheme.shapes.small,
            color = color,
        ) { RecentRowContent(day, chevron = true) }
    } else {
        Surface(modifier = tagged, shape = MaterialTheme.shapes.small, color = color) {
            RecentRowContent(day, chevron = false)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentRowContent(day: HistoryDay, chevron: Boolean) {
    Row(
        modifier = Modifier.padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = day.headline,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                day.games.forEach { ResultChip(it) }
            }
        }
        if (chevron) {
            Icon(
                imageVector = PuzzleIcons.Chevron,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One game's result that day: its inline glyph and the solve time, or "–". */
@Composable
private fun ResultChip(game: HistoryDay.GameResult) {
    Surface(shape = PillShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            GameGlyph(game.brand, GameGlyphSize.Inline)
            Text(
                text = game.resultText,
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** The game's solve time, or "–" when it was opened but not solved. */
internal val HistoryDay.GameResult.resultText: String get() = time ?: "–"
