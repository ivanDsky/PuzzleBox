package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular

private enum class TimesTab(val label: String, val tag: String) {
    Top("Top", "play.stats.tab.top"),
    NearYou("Near you", "play.stats.tab.near"),
}

private val RowHeight = 44.dp
private val AvatarSize = 28.dp
private val RankWidth = 24.dp

// Top is five rows plus the player's pinned one, the tallest list either tab draws; the
// list area always reserves it, so loading, notes and either tab share one height.
private const val LIST_ROWS = 6

/**
 * Today's times on this game's board (A15): Top (the first five, the player's own row
 * pinned under them when it ranks lower) or Near you (two ranks either side of the
 * player). There is no friend graph yet, so neither tab claims to be friends. Signed
 * out, loading, unavailable and empty boards show a note in the same reserved area.
 */
@Composable
internal fun PlayTimesCard(times: PlayTimes, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf(TimesTab.Top) }
    StatsCard(modifier.testTag("play.stats.times")) {
        CardLabel("Times today", Modifier.padding(bottom = Spacing.sm))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            TimesTab.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = tab == entry,
                    onClick = { tab = entry },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = TimesTab.entries.size),
                    enabled = times is PlayTimes.Board,
                    modifier = Modifier.testTag(entry.tag),
                ) { Text(entry.label) }
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = Spacing.sm).height(RowHeight * LIST_ROWS)) {
            val rows = (times as? PlayTimes.Board)?.let { if (tab == TimesTab.Top) it.top else it.nearYou }
            val note = noteFor(times, rows)
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = Spacing.lg).testTag("play.stats.times.note"),
                )
            } else if (rows != null) {
                Column { rows.forEach { TimeRow(it) } }
            }
        }
    }
}

private fun noteFor(times: PlayTimes, rows: List<PlayTimeRow>?): String? = when (times) {
    PlayTimes.Loading -> "Loading today's times…"
    PlayTimes.SignedOut -> "Sign in on Profile to see today's times"
    PlayTimes.Unavailable -> "Today's times aren't available right now"
    is PlayTimes.Board -> when {
        times.rows.isEmpty() -> "No times posted yet today"
        rows == null -> "Finish to see who's near you"
        else -> null
    }
}

@Composable
private fun TimeRow(row: PlayTimeRow) {
    val colors = MaterialTheme.colorScheme
    val ink = if (row.you) colors.onPrimaryContainer else colors.onSurface
    val name = if (row.you) "You" else row.name
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .then(if (row.you) Modifier.background(colors.primaryContainer, AppShapes.small) else Modifier)
            .padding(horizontal = Spacing.sm)
            .semantics(mergeDescendants = true) {}
            .testTag(if (row.you) "play.stats.row.you" else "play.stats.row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            row.rank.toString(),
            style = MaterialTheme.typography.labelLarge.tabular(),
            color = if (row.you) ink else colors.onSurfaceVariant,
            modifier = Modifier.width(RankWidth),
        )
        Box(Modifier.size(AvatarSize).background(colors.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
        }
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(formatDuration(row.timeMs), style = MaterialTheme.typography.titleSmall.tabular(), fontWeight = FontWeight.Bold, color = ink)
    }
}
