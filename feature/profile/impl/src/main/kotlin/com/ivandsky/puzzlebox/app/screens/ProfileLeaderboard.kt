package com.ivandsky.puzzlebox.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.TodayLeaderboard

/**
 * Leaderboard v1 (brief §3): today's board, name + time + hints, own row highlighted.
 * The copy names no single game (A20).
 */
@Composable
internal fun LeaderboardSection(board: TodayLeaderboard.Board, myUid: String) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("leaderboard"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Today's leaderboard",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        when (board) {
            TodayLeaderboard.Board.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp).testTag("leaderboard-loading"),
                )
            }
            // A7: a null snapshot is the graceful "not right now", shown as a soft
            // state, never an error.
            TodayLeaderboard.Board.Unavailable -> {
                Text(
                    text = "Leaderboard unavailable right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is TodayLeaderboard.Board.Loaded -> if (board.snapshot.entries.isEmpty()) {
                Text(
                    text = "No one is on today's board yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                board.snapshot.entries.forEachIndexed { index, entry ->
                    LeaderboardRow(
                        rank = index + 1,
                        entry = entry,
                        isSelf = entry.uid == myUid,
                    )
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(rank: Int, entry: LeaderboardEntry, isSelf: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelf) {
                    Modifier
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .testTag("leaderboard-row-self")
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$rank.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = entry.displayName.ifEmpty { "Player" },
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelf) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        )
        Text(
            text = "${formatDuration(entry.timeMs)} · ${entry.hints} ${if (entry.hints == 1) "hint" else "hints"}",
            style = MaterialTheme.typography.bodyMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
