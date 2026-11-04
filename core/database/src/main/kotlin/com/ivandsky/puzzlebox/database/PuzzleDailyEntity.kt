package com.ivandsky.puzzlebox.database

import androidx.room.Entity

/**
 * The real daily store (schema v2, ticket 12): one row per (gameId, day) — the Puzzle
 * envelope (ADR-0004) plus the fetch-time identity core/data owns per ADR-0002/0004.
 * The codec payload is self-describing ([formatVersion], A19); identity never rides the
 * bytes ([puzzleNumber] is the LinkedIn edition "#592"-style number, null for undated
 * sources). [day] is the ISO date in the game's cadence zone (Zip: America/Los_Angeles).
 */
@Entity(tableName = "puzzle_dailies", primaryKeys = ["gameId", "day"])
data class PuzzleDailyEntity(
    val gameId: String,
    val day: String,
    val puzzleNumber: Int?,
    val variant: String,
    val formatVersion: Int,
    val payload: ByteArray,
    val fetchedAtMs: Long,
) {
    // ByteArray identity: rows compare by content, not reference (Room itself is indifferent).
    override fun equals(other: Any?): Boolean =
        other is PuzzleDailyEntity &&
            gameId == other.gameId &&
            day == other.day &&
            puzzleNumber == other.puzzleNumber &&
            variant == other.variant &&
            formatVersion == other.formatVersion &&
            payload.contentEquals(other.payload) &&
            fetchedAtMs == other.fetchedAtMs

    override fun hashCode(): Int {
        var h = gameId.hashCode()
        h = 31 * h + day.hashCode()
        h = 31 * h + (puzzleNumber ?: 0)
        h = 31 * h + variant.hashCode()
        h = 31 * h + formatVersion
        h = 31 * h + payload.contentHashCode()
        h = 31 * h + fetchedAtMs.hashCode()
        return h
    }
}
