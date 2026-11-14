package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppDropdownMenu
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.gameui.replay.ReplayCopy
import com.ivandsky.puzzlebox.gameui.replay.ReplayTags
import com.ivandsky.puzzlebox.gameui.replay.ReplayUnavailable
import com.ivandsky.puzzlebox.history.COMPARE
import com.ivandsky.puzzlebox.history.COMPARE_DESCRIPTION
import com.ivandsky.puzzlebox.history.FIRST_SOLVE
import com.ivandsky.puzzlebox.history.HistoryReplays
import com.ivandsky.puzzlebox.history.LATEST
import com.ivandsky.puzzlebox.history.ReplaySolve

/** An icon button of the solve card: the least a finger can hit. */
internal val SolveButtonSize = 48.dp

/** Why the day has no replay to watch, as the disabled replay button tells assistive tech; null when it has one or shows none. */
internal fun HistoryReplays.unavailableText(): String? = when ((this as? HistoryReplays.Unavailable)?.reason) {
    ReplayUnavailable.ImportedFromLinkedIn -> "${ReplayCopy.NO_REPLAY} · imported from LinkedIn"
    ReplayUnavailable.NotRecorded -> "${ReplayCopy.NO_REPLAY} for this solve"
    null -> null
}

/**
 * The solve card's way into the day's [replays], as one icon button: a tap opens the one replay ([onOpen]); with two
 * different solves it drops a menu of the first solve, the latest and their compare ([onCompare]). A day whose solve
 * has no replay keeps the button, disabled, and it says why ([unavailableText]); the card's label only says "No replay".
 * A day that shows none has no button.
 */
@Composable
internal fun SolveReplayButton(replays: HistoryReplays, onOpen: (ReplaySolve) -> Unit, onCompare: () -> Unit, modifier: Modifier = Modifier) {
    when (replays) {
        HistoryReplays.Hidden -> Unit
        is HistoryReplays.Unavailable -> IconButton(onClick = {}, enabled = false, modifier = modifier.size(SolveButtonSize).testTag(ReplayTags.UNAVAILABLE)) {
            Icon(PuzzleIcons.Play, contentDescription = "${ReplayCopy.WATCH}, ${replays.unavailableText()}")
        }
        is HistoryReplays.One -> IconButton(onClick = { onOpen(replays.solve) }, modifier = modifier.size(SolveButtonSize).testTag(ReplayTags.WATCH_ROW)) {
            Icon(PuzzleIcons.Play, contentDescription = "${ReplayCopy.WATCH}, ${replays.timeline.summary()}")
        }
        is HistoryReplays.FirstAndLatest -> {
            var open by remember { mutableStateOf(false) }
            Box(modifier) {
                IconButton(onClick = { open = true }, modifier = Modifier.size(SolveButtonSize).testTag(ReplayTags.WATCH_ROW)) {
                    Icon(PuzzleIcons.Play, contentDescription = "${ReplayCopy.WATCH}: $FIRST_SOLVE, $LATEST or $COMPARE")
                }
                AppDropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.testTag("ranks.yoursolve.replays")) {
                    SolveItem(FIRST_SOLVE, replays.first) { open = false; onOpen(ReplaySolve.Record) }
                    SolveItem(LATEST, replays.latest) { open = false; onOpen(ReplaySolve.Latest) }
                    DropdownMenuItem(
                        text = { Text(COMPARE, style = MaterialTheme.typography.bodyLarge) },
                        onClick = { open = false; onCompare() },
                        modifier = Modifier.semantics { contentDescription = COMPARE_DESCRIPTION },
                    )
                }
            }
        }
    }
}

/** One solve of the day in the menu: its [label], and how long its replay is in moves and time. */
@Composable
private fun SolveItem(label: String, timeline: Timeline, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        trailingIcon = {
            Text(
                timeline.summary(),
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        },
        onClick = onClick,
    )
}

private fun Timeline.summary(): String = "${ReplayCopy.moves(moveTimesMs.size)} · ${formatDuration(endMs)}"

// The button for a solve with no replay, beside the reason it reads out; a replay's own button needs a recorded solve.
@PuzzleBoxPreviews
@Composable
private fun SolveReplayButtonPreview() {
    RanksPreviewHost {
        val replays = HistoryReplays.Unavailable(ReplayUnavailable.ImportedFromLinkedIn)
        Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SolveReplayButton(replays, onOpen = {}, onCompare = {})
            Text(replays.unavailableText().orEmpty(), style = MaterialTheme.typography.bodySmall)
        }
    }
}
