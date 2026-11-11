package com.ivandsky.puzzlebox.gameui.board

/** The key of the divider over the pinned row: an item of its own, which only fades in and out. */
internal const val DIVIDER_KEY = "divider"

// The own row is one row wherever it is drawn (in the top list or pinned) and whatever put it on the board (the
// profile's row, the row named like the LinkedIn name, the LinkedIn row added), so it slides instead of leaving and arriving.
private const val SELF_KEY = "self"

/**
 * The identity of a line, which stays the same whatever place it moves to and whatever day the board shows: a PuzzleBox
 * row is its player (the rank is a place, not a person), a connection is its member ref, the own row is always the
 * self key. The prefixes keep the three apart.
 */
internal fun DailyBoardLine.identity(): String = when (this) {
    is DailyBoardLine.Real -> if (row.isSelf) SELF_KEY else "player:${row.playerId}"
    is DailyBoardLine.Ghost -> "connection:${row.memberRef}"
}

/**
 * The keys of [lines], in order; the last one is the pinned own row when [pinned], which is the self key whatever its
 * flag says. A key met twice gets its occurrence appended, so a malformed list still gets distinct keys.
 */
internal fun boardKeys(lines: List<DailyBoardLine>, pinned: Boolean = false): List<String> {
    val seen = HashMap<String, Int>()
    return lines.mapIndexed { index, line ->
        val base = if (pinned && index == lines.lastIndex) SELF_KEY else line.identity()
        val occurrence = seen.merge(base, 1, Int::plus)!!
        if (occurrence == 1) base else "$base#$occurrence"
    }
}
