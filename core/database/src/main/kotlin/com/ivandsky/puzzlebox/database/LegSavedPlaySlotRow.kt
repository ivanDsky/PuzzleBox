package com.ivandsky.puzzlebox.database

/** Slim projection of a leg_saved_plays row for observing the duel slot list — never loads the blob. */
data class LegSavedPlaySlotRow(val matchCode: String, val legIndex: Int, val gameId: String, val elapsedMs: Long)
