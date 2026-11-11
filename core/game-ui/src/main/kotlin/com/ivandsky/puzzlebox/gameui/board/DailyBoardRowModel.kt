package com.ivandsky.puzzlebox.gameui.board

/**
 * One ranked row's data. [photo] is the absolute path of a cached photo, null while there is none (the initial shows).
 * [fromLinkedIn] marks the player's own LinkedIn row added to a board they are not on: it has no PuzzleBox [rank].
 */
data class DailyBoardRowModel(
    val rank: Int,
    val name: String,
    val timeMs: Long,
    val hints: Int,
    val isSelf: Boolean,
    val photo: String? = null,
    val fromLinkedIn: Boolean = false,
)
