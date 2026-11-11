package com.ivandsky.puzzlebox.gameui.board

/** A LinkedIn connection's result shown on a board; a null [timeMs] means the time is hidden. */
data class GhostRowModel(val name: String, val timeMs: Long?, val hintFree: Boolean)
