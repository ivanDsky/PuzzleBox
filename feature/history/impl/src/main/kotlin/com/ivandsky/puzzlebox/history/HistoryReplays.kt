package com.ivandsky.puzzlebox.history

import com.ivandsky.puzzlebox.game.replay.Timeline

/** The replays of the open History day. [Hidden] when neither exists (S11). */
sealed interface HistoryReplays {
    data object Hidden : HistoryReplays

    /**
     * One solve with a replay, or the latest solve is the record solve. [solve] says which ref opens it: the
     * latest solve when only it has a replay, the record solve otherwise.
     */
    class One(val timeline: Timeline, val solve: ReplaySolve) : HistoryReplays

    /** Two different solves. */
    class FirstAndLatest(val first: Timeline, val latest: Timeline) : HistoryReplays
}

/** Folds the record and latest replays of a day, [latestIsRecord] saying whether they are one solve. */
internal fun historyReplays(record: Timeline?, latest: Timeline?, latestIsRecord: Boolean): HistoryReplays = when {
    record == null && latest == null -> HistoryReplays.Hidden
    record == null -> HistoryReplays.One(checkNotNull(latest), solve = ReplaySolve.Latest)
    latest == null || latestIsRecord -> HistoryReplays.One(record, solve = ReplaySolve.Record)
    else -> HistoryReplays.FirstAndLatest(first = record, latest = latest)
}
