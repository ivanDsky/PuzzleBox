package com.ivandsky.puzzlebox.insights.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.maxContentWidth
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.label

/**
 * The Insights hub: a bar with a back arrow over the years and then the months that have a recap, newest first.
 * [onOpen] opens one period's story; [onBack] leaves the hub. [onOpenStats] adds the "Stats for all games" row above
 * the periods and opens the all-games stats page; null (the page is dark) leaves the row out.
 */
@Composable
internal fun InsightsHubScreen(
    state: InsightsHubUiState,
    onOpen: (RecapPeriod) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenStats: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxSize().testTag("insights.hub")) {
        CompactTopBar(
            title = "Insights",
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("insights.hub.back")) {
                    Icon(PuzzleIcons.Back, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
        when (state) {
            InsightsHubUiState.Loading -> Box(Modifier.fillMaxSize())
            InsightsHubUiState.Restoring -> EmptyState(
                title = "Bringing back your history…",
                message = "Your solved days will show up here in a moment.",
                modifier = Modifier.fillMaxSize().testTag("insights.hub.restoring"),
            )
            InsightsHubUiState.Empty -> EmptyState(
                title = "No recaps yet",
                message = "Solve a puzzle and its month shows up here.",
                modifier = Modifier.fillMaxSize().testTag("insights.hub.empty"),
            )
            is InsightsHubUiState.Ready -> PeriodList(state.periods, onOpen, onOpenStats)
        }
    }
}

@Composable
private fun PeriodList(periods: List<RecapPeriod>, onOpen: (RecapPeriod) -> Unit, onOpenStats: (() -> Unit)?) {
    LazyColumn(
        modifier = Modifier.maxContentWidth().testTag("insights.hub.list"),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (onOpenStats != null) {
            item(key = "stats") { HubRow("Stats for all games", onClick = onOpenStats, tag = "insights.hub.stats") }
        }
        items(periods, key = { it.id }) { period ->
            val kind = if (period is RecapPeriod.Year) "year" else "month"
            HubRow(period.label(), onClick = { onOpen(period) }, tag = "insights.hub.$kind.${period.id}")
        }
    }
}

/** One hub row: its [label] and a chevron, as tall as a comfortable tap (at least 56dp). */
@Composable
private fun HubRow(label: String, onClick: () -> Unit, tag: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.sm)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(PuzzleIcons.Chevron, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
