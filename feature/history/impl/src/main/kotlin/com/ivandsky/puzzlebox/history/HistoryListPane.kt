package com.ivandsky.puzzlebox.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val SelectedBorder = 2.dp
private val DAY_GROUP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

/**
 * History's list column (spec A18): the tabs, the tab's stats and weeks, then its paged
 * rows grouped under a day label. A tab with nothing in it shows only the empty state.
 */
@Composable
internal fun ListPane(
    days: LazyPagingItems<HistoryDayRow>,
    state: HistoryUiState,
    selectedTab: GameId?,
    onSelectTab: (GameId?) -> Unit,
    selectedKey: String?,
    onDayClick: (HistoryDayRow) -> Unit,
    onBack: () -> Unit,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("history.list"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(bottom = Spacing.xl),
    ) {
        item(key = "top") {
            CompactTopBar(
                title = "History",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = PuzzleIcons.Back,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
        item(key = "tabs") {
            HistoryTabs(tabs = state.tabs, selected = selectedTab, onSelect = onSelectTab)
        }
        if (days.loadState.refresh is LoadState.NotLoading && days.itemCount == 0) {
            item(key = "empty") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
                    shape = MaterialTheme.shapes.large,
                    color = cardColor,
                ) {
                    EmptyState(
                        title = "Nothing here yet",
                        message = "Solve a puzzle and it shows up here.",
                        modifier = Modifier.testTag("history.empty"),
                    )
                }
            }
            return@LazyColumn
        }
        // Stats and weeks are always items 2 and 3 (rows start at 4), filled once they load.
        item(key = "stats") {
            state.stats?.let { stats ->
                HistoryStatsHeader(
                    stats = stats,
                    perGame = selectedTab != null,
                    cardColor = cardColor,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        }
        item(key = "weeks") {
            if (state.weeks.isNotEmpty()) {
                HistoryWeekStrips(
                    weeks = state.weeks,
                    total = state.dotsPerDay,
                    cardColor = cardColor,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        }
        items(
            count = days.itemCount,
            // Game-keyed identity: two games can share a day — the key never
            // collides (ADR-0004 conventions, tango-05).
            key = days.itemKey { it.key },
            contentType = { "row" },
        ) { index ->
            val row = days[index] ?: return@items
            Column(
                modifier = Modifier.padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                // A day's first row carries its label; peek never triggers a page load.
                if (index == 0 || days.peek(index - 1)?.day != row.day) {
                    Text(
                        text = dayGroupLabel(row.day, state.today),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = Spacing.md, start = Spacing.xs)
                            .testTag("history.day.${row.day}"),
                    )
                }
                HistoryRowView(
                    row = row,
                    selected = row.key == selectedKey,
                    onClick = { onDayClick(row) },
                    cardColor = cardColor,
                )
            }
        }
    }
}

/** "Today", "Yesterday", or "Sat, Nov 1" — the list's day label. */
internal fun dayGroupLabel(day: LocalDate, today: LocalDate?): String = when (day) {
    today -> "Today"
    today?.minusDays(1) -> "Yesterday"
    else -> day.format(DAY_GROUP_FORMAT)
}

/** "No hints", "1 hint", "3 hints". */
internal fun hintsLabel(hints: Int): String = when (hints) {
    0 -> "No hints"
    1 -> "1 hint"
    else -> "$hints hints"
}

@Composable
private fun HistoryRowView(
    row: HistoryDayRow,
    selected: Boolean,
    onClick: () -> Unit,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    val accent = row.brand.accent.colors()
    OutlinedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("history.row.${row.key}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = cardColor),
        border = if (selected) BorderStroke(SelectedBorder, accent.accent) else CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            GameGlyph(brand = row.brand, size = GameGlyphSize.Tile)
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.gameName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = row.edition,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val record = row.record
            if (record == null) {
                Text(
                    text = "Keep going",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("history.unfinished.${row.key}"),
                )
            } else {
                if (row.isBest) BestPill(row.brand, Modifier.testTag("history.best.${row.key}"))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatDuration(record.time),
                        style = MaterialTheme.typography.titleMedium.tabular(),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = hintsLabel(record.hints),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The game's fastest numbered solve, in its accent (history.md §2). */
@Composable
internal fun BestPill(brand: GameBrand, modifier: Modifier = Modifier) {
    val accent = brand.accent.colors()
    Surface(
        modifier = modifier,
        shape = PillShape,
        color = accent.container,
        contentColor = accent.onContainer,
    ) {
        Text(
            text = "Best",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs / 2),
        )
    }
}
