package com.ivandsky.puzzlebox.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.gameui.bestScoreText

/**
 * The tab's numbers (spec A18, history.md §2): days in a row with the best run beside it,
 * then Solved · Best · Hint-free — and Average on a game's tab, where one game's times
 * compare. Practice never counts ([GameStats] already leaves it out).
 */
@Composable
internal fun HistoryStatsHeader(
    stats: GameStats,
    perGame: Boolean,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedCard(
        modifier = modifier.fillMaxWidth().testTag("history.stats"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = cardColor),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = "${stats.currentStreak}",
                    style = MaterialTheme.typography.headlineMedium.tabular(),
                    color = scheme.onSurface,
                    modifier = Modifier.testTag("history.stats.streak"),
                )
                Text(
                    text = if (stats.currentStreak == 1) "day in a row" else "days in a row",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f).padding(bottom = Spacing.xs),
                )
                Text(
                    text = "Best ${stats.bestStreak}",
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.xs),
                )
            }
            HorizontalDivider(color = scheme.outlineVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatTile("${stats.solved}", "Solved", Modifier.weight(1f))
                StatTile(
                    value = stats.best?.let { bestScoreText(it, stats.rankOrder) } ?: "–",
                    label = "Best",
                    modifier = Modifier.weight(1f).testTag("history.stats.best"),
                )
                StatTile("${stats.hintFree}", "No hints", Modifier.weight(1f))
                if (perGame) {
                    StatTile(stats.averageTimeMs?.let(::formatDuration) ?: "–", "Average", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
