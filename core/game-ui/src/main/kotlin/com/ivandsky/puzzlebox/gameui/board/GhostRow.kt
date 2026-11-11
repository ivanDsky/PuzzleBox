package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.linkedin.linkedInScore
import com.ivandsky.puzzlebox.gameui.PlayerAvatar
import com.ivandsky.puzzlebox.gameui.bestScoreText

private val RankWidth = 28.dp
private val GhostBorder = 1.dp
private val AvatarSize = 32.dp

/**
 * A LinkedIn connection's [row], drawn like a [DailyBoardRow] but outlined: its [place] in the board's one ranking, or
 * "—" while its time is hidden; the detail line says whose result it is and that it stays on this phone. A hint-free
 * time reads in [order]'s voice; a hinted one shows the time alone, because a ghost's real hint count is unknown.
 */
@Composable
fun GhostRow(row: GhostRowModel, place: Int?, order: RankOrder, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(GhostBorder, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .testTag("daily_board.ghost"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerAvatar(row.name.ifEmpty { "Connection" }, photoUrl = row.photo, size = AvatarSize, tone = DuelTone.Them)
        Box(Modifier.width(RankWidth)) {
            Text(
                place?.toString() ?: "—",
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = muted,
                modifier = Modifier.testTag("daily_board.ghost.rank"),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                row.name.ifEmpty { "Connection" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("daily_board.ghost.name"),
            )
            Text(
                "LinkedIn · on this phone",
                style = MaterialTheme.typography.labelSmall,
                color = muted,
                modifier = Modifier.testTag("daily_board.ghost.detail"),
            )
        }
        Text(
            scoreText(row, order),
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = if (row.timeMs == null) muted else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("daily_board.ghost.score"),
        )
    }
}

private fun scoreText(row: GhostRowModel, order: RankOrder): String {
    val timeMs = row.timeMs ?: return "time hidden"
    return if (row.hintFree) bestScoreText(linkedInScore(timeMs, true), order) else formatDuration(timeMs)
}
