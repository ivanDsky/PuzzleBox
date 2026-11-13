package com.ivandsky.puzzlebox.gameui.board

/**
 * One ranked row's data. [playerId] names the player the row is, whichever place they hold: it is the row's identity
 * where the board moves rows about, and the rank is not. [photo] is the absolute path of a cached photo, null while there is none (the initial shows).
 * [fromLinkedIn] marks the player's own LinkedIn row added to a board they are not on: it has no PuzzleBox [rank].
 * [timeFromLinkedIn] says the shown [timeMs] is a LinkedIn one, whether on that added row or on a PuzzleBox row whose
 * time a LinkedIn result replaced.
 * The score column shows [timeMs] and [hints] unless the row has a [value] of its own (an average, or points), see [scoreValue].
 * [beyondPage] shows "100+" in place of a number, for a player outside the top the board reads. [unofficial] marks the
 * viewer's device-only result: the row sits where it would rank and takes no place from anyone.
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
    val value: BoardRowValue? = null,
    val beyondPage: Boolean = false,
    val unofficial: Boolean = false,
) {
    /** What the score column says: the row's own [value], else its [timeMs] and [hints] as they stand now. */
    val scoreValue: BoardRowValue get() = value ?: BoardRowValue.Timed(timeMs, hints)
}
