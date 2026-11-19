package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import kotlin.math.roundToLong

/** Players ranked by total percentile points over a window's boards (A10). */
data class OverallStandings(val rows: List<Row>) {
    /**
     * @property games boards the player is counted on, including any past the read page.
     * @property truncated some of those boards are past the read page, so [total] is a bound.
     */
    data class Row(
        val uid: String,
        val displayName: String,
        val place: Int,
        val total: Double,
        val games: Int,
        val truncated: Boolean,
    )
}

/** Totals within this of each other are one place; sums of thirds differ by float noise. */
private const val TOTAL_SCALE = 1e9

private class Tally(val uid: String) {
    var displayName = ""
    var total = 0.0
    var games = 0
    var truncated = false
}

/**
 * Sums each player's points over [boards] (A9, A10). Places are competition places by
 * total; equal totals list by games descending, then uid. [viewerUid] on a board in
 * [viewerBeyondPage] is past that board's read page: they score [pointsBeyondPage], count
 * as played, and the row is marked truncated (A11). Their name comes from any board they
 * appear on, else is empty.
 */
fun overall(
    boards: List<RankedBoard>,
    viewerUid: String?,
    viewerBeyondPage: Set<LeaderboardRef>,
): OverallStandings {
    val tallies = LinkedHashMap<String, Tally>()
    for (board in boards) {
        for ((entry, _, points) in board.rows) {
            val tally = tallies.getOrPut(entry.uid) { Tally(entry.uid) }
            tally.displayName = entry.displayName
            tally.total += points
            tally.games++
        }
        if (viewerUid != null && board.board in viewerBeyondPage && board.rows.none { it.entry.uid == viewerUid }) {
            val tally = tallies.getOrPut(viewerUid) { Tally(viewerUid) }
            tally.total += pointsBeyondPage(board.n)
            tally.games++
            tally.truncated = true
        }
    }
    val sorted = tallies.values.sortedWith(
        compareByDescending<Tally> { key(it.total) }.thenByDescending { it.games }.thenBy { it.uid },
    )
    var place = 1
    val rows = sorted.mapIndexed { index, tally ->
        if (index > 0 && key(sorted[index - 1].total) != key(tally.total)) place = index + 1
        OverallStandings.Row(tally.uid, tally.displayName, place, tally.total, tally.games, tally.truncated)
    }
    return OverallStandings(rows)
}

private fun key(total: Double): Long = (total * TOTAL_SCALE).roundToLong()
