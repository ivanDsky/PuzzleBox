package com.ivandsky.puzzlebox.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.replay.ReplaySection
import com.ivandsky.puzzlebox.gameui.replay.ReplaySectionState
import com.ivandsky.puzzlebox.model.GameId

/**
 * What the screen needs to show the open day's replays: the [replays], each game's play UI, and the two ways out.
 * [onOpenDay] reports the day that opened, or null when the detail closed; [onOpen] opens one solve of a day.
 */
class HistoryReplayHost(
    val replays: HistoryReplays,
    val playUi: (GameId) -> GamePlayUi?,
    val onOpenDay: (HistoryDayRow?) -> Unit,
    val onOpen: (HistoryDayRow, latest: Boolean) -> Unit,
) {
    companion object {
        /** No replays: every day shows none. */
        val None = HistoryReplayHost(HistoryReplays.Hidden, { null }, {}, { _, _ -> })
    }
}

internal object HistoryReplayTags {
    const val FIRST_LABEL = "history.replay.first"
    const val LATEST_LABEL = "history.replay.latest"
}

private const val FIRST_SOLVE = "First solve"
private const val LATEST = "Latest"

/**
 * The replays of the open day: nothing for [HistoryReplays.Hidden] or a game without a replay board, one Watch replay
 * row for [HistoryReplays.One], and a "First solve" and a "Latest" row for [HistoryReplays.FirstAndLatest]. [onOpen]
 * says which solve to open: true for the latest one, false for the record one.
 */
@Composable
internal fun HistoryReplaySection(
    replays: HistoryReplays,
    ui: GamePlayUi?,
    onOpen: (latest: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ui?.replayBoard == null) return
    when (replays) {
        HistoryReplays.Hidden -> Unit
        is HistoryReplays.One -> ReplaySection(
            state = ReplaySectionState.Ready(replays.timeline),
            ui = ui,
            onOpen = { onOpen(replays.latest) },
            modifier = modifier,
        )
        is HistoryReplays.FirstAndLatest -> Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            LabelledReplay(FIRST_SOLVE, HistoryReplayTags.FIRST_LABEL, replays.first, ui) { onOpen(false) }
            LabelledReplay(LATEST, HistoryReplayTags.LATEST_LABEL, replays.latest, ui) { onOpen(true) }
        }
    }
}

@Composable
private fun LabelledReplay(label: String, tag: String, timeline: Timeline, ui: GamePlayUi, onOpen: () -> Unit) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(tag),
        )
        ReplaySection(ReplaySectionState.Ready(timeline), ui, onOpen)
    }
}
