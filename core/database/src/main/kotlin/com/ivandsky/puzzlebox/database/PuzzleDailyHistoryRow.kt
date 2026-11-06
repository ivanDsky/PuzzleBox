package com.ivandsky.puzzlebox.database

/** Slim projection of a puzzle_dailies row for history and puzzle-number reads — never loads the payload. */
data class PuzzleDailyHistoryRow(
    val day: String,
    val puzzleNumber: Int?,
    val variant: String,
    val fetchedAtMs: Long,
)
