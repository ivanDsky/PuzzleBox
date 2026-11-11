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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.ResultBeats
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.resultBeat
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayerAvatar

private val CheckSize = 16.dp
private val RowAvatar = 20.dp
private val BasisMaxWidth = 160.dp

/**
 * The puzzles list: a "Puzzles {N}" header over an outlined card, one row per puzzle in order. Rows fade in one after
 * another, the stagger capped by [ResultBeats].
 */
@Composable
internal fun SummaryPuzzleList(summary: DuelSummary, brands: GamePlayRegistry, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Puzzles", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = summary.rows.size.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
        }
        OutlinedCard(Modifier.fillMaxWidth().testTag("reveal.summary.puzzles")) {
            Column {
                summary.rows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PuzzleRowView(row, summary, brands, Modifier.resultBeat(ResultBeats.Blocks, index))
                }
            }
        }
    }
}

/** The glyph tile, "{Game} #{n}" with the basis right-aligned in the game's accent, and each player's avatar and time. */
@Composable
private fun PuzzleRowView(row: PuzzleRow, summary: DuelSummary, brands: GamePlayRegistry, modifier: Modifier) {
    val brand = brands.brandOf(row.gameId)
    val accent = brand.accent.colors().accent
    Row(
        modifier = modifier.fillMaxWidth().padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        GameGlyph(brand, GameGlyphSize.Tile)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = row.basis,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = BasisMaxWidth),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                TimeLine(summary.me, summary.myPhotoUrl, DuelTone.Me, row.mine, won = row.winner == Side.Mine, tied = row.tied, accent)
                TimeLine(summary.opponent, summary.theirPhotoUrl, DuelTone.Them, row.theirs, won = row.winner == Side.Theirs, tied = row.tied, accent)
            }
        }
    }
}

/** One player's avatar and time: the winner's in bold with a check, a tie's after "=", "Gave up" and "No result" muted. */
@Composable
private fun TimeLine(name: String, photoUrl: String?, tone: DuelTone, time: String, won: Boolean, tied: Boolean, accent: Color) {
    val scheme = MaterialTheme.colorScheme
    val missing = time == NO_RESULT || time == GAVE_UP
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        PlayerAvatar(name, photoUrl, size = RowAvatar, tone = tone)
        Text(
            text = if (tied && !missing) "= $time" else time,
            style = MaterialTheme.typography.bodyMedium.tabular(),
            fontWeight = if (won) FontWeight.Bold else FontWeight.Normal,
            color = if (missing) scheme.onSurfaceVariant else scheme.onSurface,
            maxLines = 1,
        )
        if (won) Icon(PuzzleIcons.Check, contentDescription = "Won", Modifier.size(CheckSize), tint = accent)
    }
}
