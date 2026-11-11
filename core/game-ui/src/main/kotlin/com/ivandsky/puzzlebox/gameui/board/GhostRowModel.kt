package com.ivandsky.puzzlebox.gameui.board

/** A LinkedIn connection's result shown on a board, [memberRef] being its stable key; a null [timeMs] means the time is hidden. */
data class GhostRowModel(val memberRef: String, val name: String, val timeMs: Long?, val hintFree: Boolean)
