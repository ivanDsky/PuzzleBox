package com.ivandsky.puzzlebox.history

import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.gameui.replay.ReplayUnavailable

/** The replays of the open History day. [Hidden] when the day has no stored solve; [Unavailable] when neither solve has one (S11). */
sealed interface HistoryReplays {
    data object Hidden : HistoryReplays

    /** A stored solve, and neither solve has a replay: the day says why. */
    data class Unavailable(val reason: ReplayUnavailable) : HistoryReplays

    /**
     * One solve with a replay, or the latest solve is the record solve. [solve] says which ref opens it: the
     * latest solve when only it has a replay, the record solve otherwise.
     */
    class One(val timeline: Timeline, val solve: ReplaySolve) : HistoryReplays

    /** Two different solves. */
    class FirstAndLatest(val first: Timeline, val latest: Timeline) : HistoryReplays
}

/**
 * Folds the record and latest replays of a day, [latestIsRecord] saying whether they are one solve. [none] is what a
 * day shows when neither solve has a replay.
 */
internal fun historyReplays(record: Timeline?, latest: Timeline?, latestIsRecord: Boolean, none: HistoryReplays): HistoryReplays = when {
    record == null && latest == null -> none
    record == null -> HistoryReplays.One(checkNotNull(latest), solve = ReplaySolve.Latest)
    latest == null || latestIsRecord -> HistoryReplays.One(record, solve = ReplaySolve.Record)
    else -> HistoryReplays.FirstAndLatest(first = record, latest = latest)
}
