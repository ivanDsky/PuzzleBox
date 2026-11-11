package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.compete.duel.Pill
import com.ivandsky.puzzlebox.compete.share.shareInvite
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.ResultBeats
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.resultBeat
import com.ivandsky.puzzlebox.designsystem.tabular

// The pieces the phone's score card and the tablet's hero both use.

private val TileMinHeight = 64.dp
private val PillIcon = 16.dp

/** The outcome as a pill: a trophy in the primary container on a win, a muted pill otherwise. */
@Composable
internal fun OutcomePill(summary: DuelSummary, win: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val tag = Modifier.testTag("reveal.summary.pill")
    if (!win) {
        Pill(summary.pill, tag, scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
        return
    }
    Row(
        modifier = tag.background(scheme.primaryContainer, PillShape).padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(PuzzleIcons.Trophy, contentDescription = null, modifier = Modifier.size(PillIcon), tint = scheme.onPrimaryContainer)
        Text(summary.pill, style = MaterialTheme.typography.labelMedium, color = scheme.onPrimaryContainer, maxLines = 1)
    }
}

/** Puzzles won, the fastest puzzle and the total time, as three tiles in a row. */
@Composable
internal fun Tiles(tiles: SummaryTiles) {
    Row(Modifier.fillMaxWidth().resultBeat(ResultBeats.Supporting), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Tile("${tiles.puzzlesWon} of ${tiles.puzzles}", "Puzzles won", "reveal.tile.won", Modifier.weight(1f))
        Tile(tiles.fastestMs?.let(::formatDuration) ?: "–", "Fastest puzzle", "reveal.tile.fastest", Modifier.weight(1f))
        Tile(formatDuration(tiles.totalMs), "Total time", "reveal.tile.total", Modifier.weight(1f))
    }
}

@Composable
private fun Tile(value: String, label: String, tag: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = TileMinHeight)
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Rematch, then Share and Watch again as a tonal pair, each with its icon. */
@Composable
internal fun Actions(summary: DuelSummary, onRematch: () -> Unit, onWatchAgain: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier.resultBeat(ResultBeats.Actions), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        PrimaryButton("Rematch", onRematch, Modifier.fillMaxWidth().testTag("reveal.rematch"))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SecondaryButton(
                "Share", { context.shareInvite(summary.shareText) },
                Modifier.weight(1f).testTag("reveal.share"), icon = PuzzleIcons.Share,
            )
            SecondaryButton(
                "Watch again", onWatchAgain,
                Modifier.weight(1f).testTag("reveal.watchAgain"), icon = PuzzleIcons.Retry,
            )
        }
    }
}
