package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.ResultBeats
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.resultBeat
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry

private val NameMaxWidth = 72.dp
private val CheckSize = 14.dp

/**
 * The puzzles list: an outlined card, one row per puzzle in order. Rows fade in one after another, the stagger capped
 * by [ResultBeats].
 */
@Composable
internal fun SummaryPuzzleList(summary: DuelSummary, brands: GamePlayRegistry, modifier: Modifier = Modifier) {
    OutlinedCard(modifier.fillMaxWidth().testTag("reveal.summary.puzzles")) {
        Column {
            summary.rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PuzzleRowView(row, summary, brands, Modifier.resultBeat(ResultBeats.Blocks, index))
            }
        }
    }
}

@Composable
private fun PuzzleRowView(row: PuzzleRow, summary: DuelSummary, brands: GamePlayRegistry, modifier: Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        GameGlyph(brands.brandOf(row.gameId), GameGlyphSize.Tile)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.basis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            TimeLine("You", row.mine, won = row.winner == Side.Mine, tied = row.tied)
            TimeLine(summary.opponent, row.theirs, won = row.winner == Side.Theirs, tied = row.tied)
        }
    }
}

/** One player's time: the winner's in bold with a check, a tie's after "=", "No result" muted. */
@Composable
private fun TimeLine(name: String, time: String, won: Boolean, tied: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val missing = time == "No result"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = NameMaxWidth),
        )
        Text(
            text = if (tied && !missing) "= $time" else time,
            style = MaterialTheme.typography.bodyMedium.tabular(),
            fontWeight = if (won) FontWeight.Bold else FontWeight.Normal,
            color = if (missing) scheme.onSurfaceVariant else scheme.onSurface,
            maxLines = 1,
        )
        if (won) Icon(PuzzleIcons.Check, contentDescription = "Won", Modifier.size(CheckSize), tint = scheme.primary)
    }
}
