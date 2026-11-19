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
 * The Insights hub: a bar with a back arrow over the months that have a recap, newest first. [onOpen] opens one
 * month's story; [onBack] leaves the hub.
 */
@Composable
internal fun InsightsHubScreen(
    state: InsightsHubUiState,
    onOpen: (RecapPeriod) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
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
            is InsightsHubUiState.Ready -> MonthList(state.periods, onOpen)
        }
    }
}

@Composable
private fun MonthList(periods: List<RecapPeriod>, onOpen: (RecapPeriod) -> Unit) {
    LazyColumn(
        modifier = Modifier.maxContentWidth().testTag("insights.hub.list"),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items(periods, key = { it.id }) { period -> MonthRow(period, onClick = { onOpen(period) }) }
    }
}

/** One month: its name and a chevron, as tall as a comfortable tap (at least 56dp). */
@Composable
private fun MonthRow(period: RecapPeriod, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.sm)
            .testTag("insights.hub.month.${period.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = period.label(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(PuzzleIcons.Chevron, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
