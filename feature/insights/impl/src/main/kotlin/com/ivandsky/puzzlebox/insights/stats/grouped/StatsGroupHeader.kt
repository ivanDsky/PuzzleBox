package com.ivandsky.puzzlebox.insights.stats.grouped

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * A group's header: its title as a heading over the page's background, so the sections that scroll under it as it sticks
 * are hidden. [divided] puts a 1dp hairline on its top (every group after the first), which sticks with it.
 */
@Composable
internal fun StatsGroupHeader(group: StatsGroup, divided: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .testTag("stats.group.${group.name.lowercase()}")
            .semantics(mergeDescendants = true) { heading() }
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (divided) HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = group.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
    }
}
