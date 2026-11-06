package com.ivandsky.puzzlebox.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag

import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing

import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DAY_GROUP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

/**
 * History's list column (spec A18): the tabs, the tab's stats and week/month calendar, then
 * its paged rows grouped under a day label. A day picked on the calendar swaps the pages
 * for that day's rows from the state, under a pill that clears it; on a game's tab the
 * day's header ([HistoryDayHeader]) sits above them and its action reports the (game, day)
 * through [onReplay]. A tab with nothing in it shows only the empty state — except a game
 * tab with an archive, which keeps its calendar so any archive day can be picked (ticket 09).
 */
@Composable
internal fun ListPane(
    days: LazyPagingItems<HistoryDayRow>,
    state: HistoryUiState,
    selectedTab: GameId?,
    onSelectTab: (GameId?) -> Unit,
    onShowCalendar: (day: LocalDate, expanded: Boolean) -> Unit,
    onSelectDay: (LocalDate?) -> Unit,
    selectedKey: String?,
    onDayClick: (HistoryDayRow) -> Unit,
    onReplay: (GameId, LocalDate) -> Unit,
    onBack: () -> Unit,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    val empty = days.loadState.refresh is LoadState.NotLoading && days.itemCount == 0
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
        if (empty && state.archiveRange == null) {
            emptyItem(cardColor)
            return@LazyColumn
        }
        // Stats and the calendar are always items 2 and 3 (rows start at 4), filled once they load.
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
        item(key = "calendar") {
            HistoryCalendar(
                state = state,
                onShowCalendar = onShowCalendar,
                onSelectDay = onSelectDay,
                cardColor = cardColor,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }
        state.selectedDay?.let { day ->
            item(key = "day_filter") {
                DayFilterPill(
                    day = day,
                    today = state.today,
                    onClear = { onSelectDay(null) },
                    modifier = Modifier.padding(start = Spacing.lg, top = Spacing.sm),
                )
            }
            val dayRows = state.rows.filter { it.day == day }
            val past = state.pastDay
            val pastTab = past?.let { state.tabs.firstOrNull { tab -> tab.gameId == it.game } }
            if (past != null && pastTab != null) {
                item(key = "day_header") {
                    HistoryDayHeader(
                        pastDay = past,
                        tab = pastTab,
                        onPlay = { onReplay(past.game, past.day) },
                        cardColor = cardColor,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
            } else if (dayRows.isEmpty()) {
                item(key = "day_empty") {
                    Text(
                        text = "Nothing played on this day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                            .testTag("history.day_empty"),
                    )
                }
            }
            items(items = dayRows, key = { it.key }, contentType = { "row" }) { row ->
                HistoryRowView(
                    row = row,
                    selected = row.key == selectedKey,
                    onClick = { onDayClick(row) },
                    cardColor = cardColor,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
            return@LazyColumn
        }
        if (empty) emptyItem(cardColor)
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

/** The tab's "Nothing here yet" card. */
private fun LazyListScope.emptyItem(cardColor: Color) {
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
}

/** "Today", "Yesterday", or "Sat, Nov 1" — the list's day label. */
internal fun dayGroupLabel(day: LocalDate, today: LocalDate?): String = when (day) {
    today -> "Today"
    today?.minusDays(1) -> "Yesterday"
    else -> day.format(DAY_GROUP_FORMAT)
}

