package com.ivandsky.puzzlebox.gameui.board

/**
 * A LinkedIn connection's result shown on a board, [memberRef] being its stable key; a null [timeMs] means the time is
 * hidden. [photo] is the absolute path of the cached photo, null while there is none.
 */
data class GhostRowModel(
    val memberRef: String,
    val name: String,
    val timeMs: Long?,
    val hintFree: Boolean,
    val photo: String? = null,
)
