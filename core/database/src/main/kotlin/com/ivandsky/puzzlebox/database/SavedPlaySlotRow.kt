package com.ivandsky.puzzlebox.database

/** Slim projection of a saved_plays row for observing the slot list — never loads the blob. */
data class SavedPlaySlotRow(
    val gameId: String,
    val day: String,
)
