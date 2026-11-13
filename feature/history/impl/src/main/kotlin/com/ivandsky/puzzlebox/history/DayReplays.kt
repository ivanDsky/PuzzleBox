package com.ivandsky.puzzlebox.history

import com.ivandsky.puzzlebox.domain.LoadSolveReplay
import com.ivandsky.puzzlebox.domain.SolveReplayRef
import com.ivandsky.puzzlebox.gameui.replay.ReplayUnavailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The replays of the day [key] names. A screen shows them only for that day, so a switch never flashes the last day's. */
class DayReplays(val key: String?, val replays: HistoryReplays)

/** Loads the replays of the open day for one calendar owner, on its [scope]; at most one load runs. */
internal class DayReplaysLoader(private val scope: CoroutineScope, private val loadReplay: LoadSolveReplay) {
    private val current = MutableStateFlow(DayReplays(null, HistoryReplays.Hidden))
    private var loading: Job? = null

    /** The replays of the open day. */
    val replays: StateFlow<DayReplays> = current.asStateFlow()

    /**
     * Loads the replays of [row]'s solve; null (the detail closed), or a row without a solve, drops
     * them. A load still running for the previous day is cancelled.
     */
    fun open(row: HistoryDayRow?) {
        loading?.cancel()
        current.value = DayReplays(row?.key, HistoryReplays.Hidden)
        val solved = row?.record ?: return
        val puzzle = solved.puzzleRef
        loading = scope.launch {
            val record = loadReplay(SolveReplayRef.Record(puzzle))
            val latest = loadReplay(SolveReplayRef.Latest(puzzle))
            val none = if (loadReplay.enabled) HistoryReplays.Unavailable(ReplayUnavailable.of(solved.source)) else HistoryReplays.Hidden
            current.value = DayReplays(row.key, historyReplays(record, latest, loadReplay.latestIsRecord(puzzle), none))
        }
    }
}
