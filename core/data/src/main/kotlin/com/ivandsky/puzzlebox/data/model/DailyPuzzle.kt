package com.ivandsky.puzzlebox.data.model

/**
 * Exemplar domain type for the repository pattern. Stays here until :core:model lands,
 * then moves there — repository interfaces expose domain types, not Room entities.
 */
data class DailyPuzzle(
    val gameId: String,
    val day: String,
    val size: Int,
    val cells: List<Int>,
)
