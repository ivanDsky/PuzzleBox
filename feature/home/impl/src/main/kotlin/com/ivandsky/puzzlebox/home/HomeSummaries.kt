package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.model.GameId

/**
 * What Today shows of content owned elsewhere (ADR-0027): one summary row each, with a
 * chevron, that reports its tap. A null field means that row is not offered.
 * [onOpenRank] gets the game of the rank row; [duelsNeedYou] is the number of duels waiting
 * on the player, read where the row composes; the row shows only above zero.
 */
class HomeSummaries(
    val onOpenRank: ((GameId) -> Unit)?,
    val onOpenDuels: (() -> Unit)?,
    val duelsNeedYou: (@Composable () -> Int)?,
)

/** The player's place on the board of the game they solved last today; [position] is 1-based. */
internal data class TodayRank(val gameId: GameId, val gameName: String, val position: Int)

/**
 * The summary rows: today's rank when [HomeUiState.rank] is set and [HomeSummaries.onOpenRank]
 * is offered, and the duels waiting on the player when the count is above zero. Emits nothing
 * at all when neither shows, so the list keeps no gap.
 */
@Composable
internal fun HomeSummaryRows(state: HomeUiState, summaries: HomeSummaries) {
    val rank = state.rank
    val onOpenRank = summaries.onOpenRank
    val onOpenDuels = summaries.onOpenDuels
    val duels = if (onOpenDuels != null) summaries.duelsNeedYou?.invoke() ?: 0 else 0
    val showRank = rank != null && onOpenRank != null
    if (!showRank && duels <= 0) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (rank != null && onOpenRank != null) {
            SummaryRow(
                text = "Today's rank: #${rank.position} on ${rank.gameName}",
                tag = "summary.leaderboard",
                onClick = { onOpenRank(rank.gameId) },
            )
        }
        if (onOpenDuels != null && duels > 0) {
            SummaryRow(
                text = if (duels == 1) "1 duel needs you" else "$duels duels need you",
                tag = "summary.duels",
                onClick = onOpenDuels,
            )
        }
    }
}

@Composable
private fun SummaryRow(text: String, tag: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(tag),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = PuzzleIcons.Chevron,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
