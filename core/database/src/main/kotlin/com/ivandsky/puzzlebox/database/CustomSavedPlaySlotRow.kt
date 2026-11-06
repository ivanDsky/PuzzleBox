package com.ivandsky.puzzlebox.database

/** Slim projection of a custom_saved_plays row for observing the friend slot list — never loads the blob. */
data class CustomSavedPlaySlotRow(val puzzleId: String, val gameId: String, val elapsedMs: Long)
