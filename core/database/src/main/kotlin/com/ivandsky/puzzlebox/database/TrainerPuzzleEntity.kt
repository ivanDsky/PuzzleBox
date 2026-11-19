package com.ivandsky.puzzlebox.database

import androidx.room.Entity

/**
 * One puzzle the Trainer fetched, cached by ([gameId], [puzzleNumber]) (schema v18). It is separate from
 * `puzzle_dailies` on purpose, so History, the archive month and Random never see a puzzle the player only
 * trained on. The [payload] is the self-describing codec bytes ([formatVersion]); [day] is the ISO date in the
 * game's cadence zone. Device content, not progress: no owner, never synced.
 */
@Entity(tableName = "trainer_puzzles", primaryKeys = ["gameId", "puzzleNumber"])
data class TrainerPuzzleEntity(
    val gameId: String,
    val puzzleNumber: Int,
    val day: String,
    val variant: String,
    val formatVersion: Int,
    val payload: ByteArray,
    val fetchedAtMs: Long,
) {
    // ByteArray identity: rows compare by content, not reference (Room itself is indifferent).
    override fun equals(other: Any?): Boolean =
        other is TrainerPuzzleEntity &&
            gameId == other.gameId &&
            puzzleNumber == other.puzzleNumber &&
            day == other.day &&
            variant == other.variant &&
            formatVersion == other.formatVersion &&
            payload.contentEquals(other.payload) &&
            fetchedAtMs == other.fetchedAtMs

    override fun hashCode(): Int {
        var h = gameId.hashCode()
        h = 31 * h + puzzleNumber
        h = 31 * h + day.hashCode()
        h = 31 * h + variant.hashCode()
        h = 31 * h + formatVersion
        h = 31 * h + payload.contentHashCode()
        h = 31 * h + fetchedAtMs.hashCode()
        return h
    }
}
