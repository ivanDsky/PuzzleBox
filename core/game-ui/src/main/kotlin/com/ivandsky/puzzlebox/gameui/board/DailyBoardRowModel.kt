package com.ivandsky.puzzlebox.gameui.board

/** One ranked row's data. */
data class DailyBoardRowModel(val rank: Int, val name: String, val timeMs: Long, val hints: Int, val isSelf: Boolean)
