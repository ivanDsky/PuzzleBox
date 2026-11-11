package com.ivandsky.puzzlebox.gameui.board

/** The key of the pinned own row and its divider, which are one item of the list. */
private const val PINNED_KEY = "pinned"

/**
 * The identity of a line, which stays the same whatever place it moves to: a PuzzleBox row is its player (the rank is
 * a place, not a person), a connection is its member ref. The prefixes keep the two apart.
 */
internal fun DailyBoardLine.identity(): String = when (this) {
    is DailyBoardLine.Real -> "player:${row.playerId}"
    is DailyBoardLine.Ghost -> "connection:${row.memberRef}"
}

/**
 * The keys of [lines], in order, for the board of [day], then the [pinned] item's when there is one. A row of another day gets another key, so a day switch is
 * rows leaving and rows arriving instead of the old day's rows sliding into the new one's places. A key met twice gets
 * its occurrence appended, so a malformed list still gets distinct keys.
 */
internal fun boardKeys(lines: List<DailyBoardLine>, day: String?, pinned: Boolean = false): List<String> {
    val seen = HashMap<String, Int>()
    val keys = lines.map { line ->
        val base = "${day.orEmpty()}|${line.identity()}"
        val occurrence = seen.merge(base, 1, Int::plus)!!
        if (occurrence == 1) base else "$base#$occurrence"
    }
    return if (pinned) keys + "${day.orEmpty()}|$PINNED_KEY" else keys
}
