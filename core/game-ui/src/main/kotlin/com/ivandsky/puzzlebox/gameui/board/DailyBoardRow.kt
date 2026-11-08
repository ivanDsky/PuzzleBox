package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.bestScoreText

private val RankWidth = 28.dp

/** One ranked [row]; the signed-in player's own row stands out on the secondary container. */
@Composable
fun DailyBoardRow(row: DailyBoardRowModel, order: RankOrder, modifier: Modifier = Modifier) {
    val content = if (row.isSelf) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (row.isSelf) Modifier.background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small) else Modifier)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .testTag(if (row.isSelf) "daily_board.row.self" else "daily_board.row"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(RankWidth)) {
            Text(
                "${row.rank}",
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = if (row.isSelf) content else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("daily_board.row.rank"),
            )
        }
        Text(
            row.name.ifEmpty { "Player" },
            style = MaterialTheme.typography.bodyLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag("daily_board.row.name"),
        )
        Text(
            bestScoreText(BestScore(row.timeMs, row.hints), order),
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = content,
            modifier = Modifier.testTag("daily_board.row.score"),
        )
    }
}
