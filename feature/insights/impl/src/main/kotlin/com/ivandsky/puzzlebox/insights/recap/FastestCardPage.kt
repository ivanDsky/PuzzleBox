package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId

/**
 * One page of the Fastest solve card: game [sub]'s page, on its brand's accent (Neutral until the route provides the
 * brands). The deck steps between the games as between cards, so this draws only the one; the replay pill is the only
 * thing on it that takes a tap (A5: an imported time has none).
 */
@Composable
internal fun FastestCardPage(
    card: RecapCard.Fastest,
    periodLabel: String,
    gameName: (GameId) -> String,
    onWatchReplay: (RecapCard.Fastest.Row) -> Unit,
    modifier: Modifier = Modifier,
    sub: Int = 0,
) {
    val row = card.rows.getOrNull(sub) ?: return
    val brand = LocalGameBrands.current(row.game)
    Box(modifier.fillMaxSize().testTag("recap.fastest")) {
        FastestGameCard(
            row,
            gameName(row.game),
            periodLabel,
            brand.glyph,
            brand.accent.colors(),
            onWatchReplay = { onWatchReplay(row) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Each game's fastest solve as one flat row of the tablet's selected fact. */
@Composable
internal fun FastestRows(
    rows: List<RecapCard.Fastest.Row>,
    gameName: (GameId) -> String,
    onWatchReplay: (RecapCard.Fastest.Row) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .testTag("recap.fastest.games"),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = MaterialTheme.colorScheme.outlineVariant)
            FastestRow(row, gameName(row.game), onWatchReplay)
        }
    }
}

/** One game's row: its name and day, the time, and the play mark where a replay opens. The row reads as one item (a button when it opens a replay). */
@Composable
private fun FastestRow(
    row: RecapCard.Fastest.Row,
    name: String,
    onWatchReplay: (RecapCard.Fastest.Row) -> Unit,
) {
    val time = formatDuration(row.timeMs)
    val day = row.day.shortLabel()
    val open = if (row.hasReplay) {
        Modifier.clickable(onClickLabel = "Watch replay", role = Role.Button) { onWatchReplay(row) }
    } else {
        Modifier
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .then(open)
            .padding(start = Spacing.lg)
            .semantics(mergeDescendants = true) {}
            .testTag("recap.fastest.game.${row.game.value}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = Spacing.sm)) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = if (row.hasReplay) day else "$day · No replay",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = time, style = MaterialTheme.typography.titleMedium.tabular(), color = MaterialTheme.colorScheme.onSurface)
        // A fixed slot, so the times of rows with and without a replay line up.
        if (row.hasReplay) {
            Icon(
                PuzzleIcons.Play,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = Spacing.sm).size(PlaySlot),
            )
        } else {
            Spacer(Modifier.size(PlaySlot + Spacing.sm * 2))
        }
    }
}

/** The Fastest solve fact in the tablet list: the title, then each game's time. */
@Composable
internal fun FastestCardFact(
    card: RecapCard.Fastest,
    gameName: (GameId) -> String,
    onWatchReplay: (RecapCard.Fastest.Row) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            FactMarker()
            Text(
                text = "Fastest solves",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        FastestRows(card.rows, gameName, onWatchReplay)
    }
}

private val RowMinHeight = 56.dp
private val PlaySlot = 32.dp
