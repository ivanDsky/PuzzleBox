package com.ivandsky.puzzlebox.history

import com.ivandsky.puzzlebox.play.api.ReplayCompareKey
import com.ivandsky.puzzlebox.play.api.ReplayKey

internal const val FIRST_SOLVE = "First solve"
internal const val LATEST = "Latest"
internal const val COMPARE = "Compare"
internal const val COMPARE_DESCRIPTION = "Compare first solve with latest"

/** The compare of [row]'s two solves: the latest is "mine" (the primary side), the first solve the other. */
internal fun historyCompareKey(row: HistoryDayRow): ReplayCompareKey {
    fun solve(latest: Boolean) = ReplayKey.daily(row.gameId.value, row.day.toString(), row.puzzleNumber, latest)
    return ReplayCompareKey.of(mine = solve(latest = true), theirs = solve(latest = false), myName = LATEST, theirName = FIRST_SOLVE)
}
