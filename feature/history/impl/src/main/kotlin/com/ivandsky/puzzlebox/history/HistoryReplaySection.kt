package com.ivandsky.puzzlebox.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.replay.ReplaySection
import com.ivandsky.puzzlebox.gameui.replay.ReplaySectionState
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.api.ReplayCompareKey
import com.ivandsky.puzzlebox.play.api.ReplayKey

/**
 * What the screen needs to show the open day's replays: the [replays], each game's play UI, and the two ways out.
 * [onOpenDay] reports the day that opened, or null when the detail closed; [onOpen] opens one solve of a day and
 * [onCompare] the day's first solve against its latest.
 */
class HistoryReplayHost(
    val replays: DayReplays,
    val playUi: (GameId) -> GamePlayUi?,
    val onOpenDay: (HistoryDayRow?) -> Unit,
    val onOpen: (HistoryDayRow, ReplaySolve) -> Unit,
    val onCompare: (HistoryDayRow) -> Unit,
) {
    companion object {
        /** No replays: every day shows none. */
        val None = HistoryReplayHost(DayReplays(null, HistoryReplays.Hidden), { null }, {}, { _, _ -> }, {})
    }
}

private const val FIRST_SOLVE = "First solve"
private const val LATEST = "Latest"
private const val COMPARE = "Compare"

/** The compare of [row]'s two solves: the latest is "mine" (the primary side), the first solve the other. */
internal fun historyCompareKey(row: HistoryDayRow): ReplayCompareKey {
    fun solve(latest: Boolean) = ReplayKey.daily(row.gameId.value, row.day.toString(), row.puzzleNumber, latest)
    return ReplayCompareKey.of(mine = solve(latest = true), theirs = solve(latest = false), myName = LATEST, theirName = FIRST_SOLVE)
}

/**
 * The replays of the open day: nothing for [HistoryReplays.Hidden] or a game without a replay board, one Watch replay
 * row for [HistoryReplays.One], and a "First solve" and a "Latest" row plus a "Compare" button for
 * [HistoryReplays.FirstAndLatest]. [onOpen] says which solve to open; [onCompare] opens both side by side.
 */
@Composable
internal fun HistoryReplaySection(
    replays: HistoryReplays,
    ui: GamePlayUi?,
    onOpen: (ReplaySolve) -> Unit,
    onCompare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ui?.replayBoard == null) return
    when (replays) {
        HistoryReplays.Hidden -> Unit
        is HistoryReplays.One -> ReplaySection(
            state = ReplaySectionState.Ready(replays.timeline),
            ui = ui,
            onOpen = { onOpen(replays.solve) },
            modifier = modifier,
        )
        is HistoryReplays.FirstAndLatest -> Column(modifier.fillMaxWidth()) {
            ReplaySection(ReplaySectionState.Ready(replays.first), ui, { onOpen(ReplaySolve.Record) }, label = FIRST_SOLVE)
            ReplaySection(ReplaySectionState.Ready(replays.latest), ui, { onOpen(ReplaySolve.Latest) }, label = LATEST)
            OutlinedButton(onClick = onCompare, modifier = Modifier.fillMaxWidth()) { Text(COMPARE) }
        }
    }
}
