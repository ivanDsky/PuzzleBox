package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.PlayerAvatar

private val HeaderMinHeight = 48.dp
private val HeaderAvatar = 20.dp

/**
 * The column heads of the puzzles table: "Puzzle", then each player's avatar and name right-aligned over their time
 * column, and on a tablet "← {me}" and "{opponent} →" over the margin bar. It is laid out with the same [metrics] as
 * the rows, so every head sits right over its column.
 */
@Composable
internal fun PuzzleTableHeader(summary: DuelSummary, metrics: TableMetrics, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HeaderMinHeight)
            .padding(horizontal = metrics.sidePadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.columnGap),
    ) {
        Text(
            text = "PUZZLE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(PuzzleCellWeight),
        )
        PlayerHead(summary.me, summary.myPhotoUrl, DuelTone.Me, Modifier.width(metrics.timeWidth))
        PlayerHead(summary.opponent, summary.theirPhotoUrl, DuelTone.Them, Modifier.width(metrics.timeWidth))
        if (!metrics.compact) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                MarginHead("← ${summary.me}", Modifier.weight(1f, fill = false))
                MarginHead("${summary.opponent} →", Modifier.weight(1f, fill = false).padding(start = Spacing.sm))
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** The share of a tablet row the puzzle's tile and name take, against 1 for the margin bar. */
internal const val PuzzleCellWeight = 1.2f

@Composable
private fun PlayerHead(name: String, photoUrl: String?, tone: DuelTone, modifier: Modifier) {
    Row(
        modifier = modifier.padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        PlayerAvatar(name, photoUrl, size = HeaderAvatar, tone = tone)
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun MarginHead(text: String, modifier: Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
