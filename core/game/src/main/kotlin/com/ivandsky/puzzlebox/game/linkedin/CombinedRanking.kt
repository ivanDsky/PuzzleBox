package com.ivandsky.puzzlebox.game.linkedin

import com.ivandsky.puzzlebox.game.BestScore

/**
 * The one ranking of a board where PuzzleBox rows ([rows]) and LinkedIn ghosts ([ghosts], a null score being a hidden
 * time) are placed together by [order]; the board, the day card and the result line all take their places from it, so
 * they cannot drift apart.
 *
 * Equal scores share a place and the next place skips past them. On a tie the row comes before the ghost, and equal
 * scores of one kind keep the order given. A hidden time is unranked (null place) and comes last, in the order given.
 * The slots are in that board order.
 */
fun rankCombined(rows: List<BestScore>, ghosts: List<BestScore?>, order: Comparator<BestScore>): List<RankedSlot> {
    val rowOrder = rows.indices.sortedWith { a, b -> order.compare(rows[a], rows[b]) }
    val ghostOrder = ghosts.indices.filter { ghosts[it] != null }.sortedWith { a, b -> order.compare(ghosts[a]!!, ghosts[b]!!) }
    val slots = ArrayList<RankedSlot>(rows.size + ghosts.size)
    var placed = 0
    var place = 0
    var previous: BestScore? = null
    fun placeOf(score: BestScore): Int {
        placed++
        val before = previous
        if (before == null || order.compare(score, before) != 0) place = placed
        previous = score
        return place
    }
    var nextGhost = 0
    for (row in rowOrder) {
        val score = rows[row]
        // Strictly better only: on a tie the row stays first.
        while (nextGhost < ghostOrder.size && order.compare(ghosts[ghostOrder[nextGhost]]!!, score) < 0) {
            val ghost = ghostOrder[nextGhost++]
            slots += RankedSlot(ghost = true, index = ghost, place = placeOf(ghosts[ghost]!!))
        }
        slots += RankedSlot(ghost = false, index = row, place = placeOf(score))
    }
    while (nextGhost < ghostOrder.size) {
        val ghost = ghostOrder[nextGhost++]
        slots += RankedSlot(ghost = true, index = ghost, place = placeOf(ghosts[ghost]!!))
    }
    ghosts.indices.filter { ghosts[it] == null }.mapTo(slots) { RankedSlot(ghost = true, index = it, place = null) }
    return slots
}

/** How many a board counts, the "of N": every ranked slot, so a hidden time is not counted. */
fun List<RankedSlot>.rankedCount(): Int = count { it.place != null }
