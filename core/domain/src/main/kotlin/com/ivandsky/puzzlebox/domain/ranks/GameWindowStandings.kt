package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.game.RankOrder

/** Players ranked on one game over a window by their average solve (A36). */
data class GameWindowStandings(val rows: List<Row>) {
    /** @property days boards in the window the average is over. */
    data class Row(
        val uid: String,
        val displayName: String,
        val place: Int,
        val avgTimeMs: Long,
        val avgHints: Double,
        val days: Int,
    )
}

private class Average(val uid: String) {
    var displayName = ""
    var timeMs = 0L
    var hints = 0L
    var days = 0
    val time get() = timeMs.toDouble() / days
    val hintsAvg get() = hints.toDouble() / days
}

/**
 * Averages each player's time and hints over the [boards] they played, then ranks by
 * [order]: hints first for Strands, time first otherwise (A36). Equal averages share a
 * place; the list breaks them by uid. Players past a board's read page have no entry
 * to average and are left out of it.
 */
fun gameWindowStandings(boards: List<RankedBoard>, order: RankOrder): GameWindowStandings {
    val averages = LinkedHashMap<String, Average>()
    for (board in boards) {
        for (row in board.rows) {
            val average = averages.getOrPut(row.entry.uid) { Average(row.entry.uid) }
            average.displayName = row.entry.displayName
            average.timeMs += row.entry.timeMs
            average.hints += row.entry.hints
            average.days++
        }
    }
    val comparator = when (order) {
        RankOrder.TimeThenHints -> compareBy<Average>({ it.time }, { it.hintsAvg })
        RankOrder.HintsThenTime -> compareBy<Average>({ it.hintsAvg }, { it.time })
    }
    val sorted = averages.values.sortedWith(comparator.thenBy { it.uid })
    var place = 1
    val rows = sorted.mapIndexed { index, average ->
        if (index > 0 && comparator.compare(sorted[index - 1], average) != 0) place = index + 1
        GameWindowStandings.Row(
            uid = average.uid,
            displayName = average.displayName,
            place = place,
            avgTimeMs = Math.round(average.time),
            avgHints = average.hintsAvg,
            days = average.days,
        )
    }
    return GameWindowStandings(rows)
}
