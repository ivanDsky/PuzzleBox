package com.ivandsky.puzzlebox.insights.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.model.GameId

/**
 * The phone's stats filter bar, which stands in for the range chips with the StatsFilters flag on: [StatsGameFilter] and
 * [StatsSinceFilter] side by side in a 52dp row over a hairline. It sits above the sections, so it stays put as they scroll.
 */
@Composable
internal fun StatsFilterBar(
    state: StatsUiState,
    games: List<GameId>,
    gameName: (GameId) -> String,
    onSelectGame: (GameId?) -> Unit,
    onSelectRange: (StatsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().testTag("stats.filters")) {
        Row(
            Modifier.fillMaxWidth().height(BarHeight).padding(horizontal = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatsGameFilter(state.game, games, gameName, onSelectGame)
            StatsSinceFilter(state.range, state.since, onSelectRange)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * The tablet's counterpart of [StatsFilterBar]: the quiet [StatsSinceFilter] alone in the same 52dp row over the same
 * hairline, across the top of the stats pane.
 */
@Composable
internal fun StatsQuietSinceBar(
    state: StatsUiState,
    onSelectRange: (StatsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().testTag("stats.filters")) {
        Row(Modifier.fillMaxWidth().height(BarHeight).padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            StatsSinceFilter(state.range, state.since, onSelectRange, quiet = true)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private val BarHeight = 52.dp
