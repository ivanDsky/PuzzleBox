package com.ivandsky.puzzlebox.gameui.board

/** One ranked row's data. [photo] is the absolute path of a cached photo, null while there is none (the initial shows). */
data class DailyBoardRowModel(
    val rank: Int,
    val name: String,
    val timeMs: Long,
    val hints: Int,
    val isSelf: Boolean,
    val photo: String? = null,
)
