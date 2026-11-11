package com.ivandsky.puzzlebox.gameui.board

/**
 * One ranked row's data. [playerId] names the player the row is, whichever place they hold: it is the row's identity
 * where the board moves rows about, and the rank is not. [photo] is the absolute path of a cached photo, null while there is none (the initial shows).
 * [fromLinkedIn] marks the player's own LinkedIn row added to a board they are not on: it has no PuzzleBox [rank].
 * [timeFromLinkedIn] says the shown [timeMs] is a LinkedIn one, whether on that added row or on a PuzzleBox row whose
 * time a LinkedIn result replaced.
 */
data class DailyBoardRowModel(
    val rank: Int,
    val name: String,
    val timeMs: Long,
    val hints: Int,
    val isSelf: Boolean,
    val photo: String? = null,
    val fromLinkedIn: Boolean = false,
    val timeFromLinkedIn: Boolean = false,
    val playerId: String,
)
