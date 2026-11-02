package com.ivandsky.puzzlebox.data.db

import androidx.room.Entity
import com.ivandsky.puzzlebox.model.DailyPuzzle

/** gameId + day form the natural key — one cached official daily per game per day. */
@Entity(tableName = "daily_puzzles", primaryKeys = ["gameId", "day"])
data class DailyPuzzleEntity(
    val gameId: String,
    val day: String,
    val payload: ByteArray,
    val fetchedAtMs: Long,
) {
    // WHY: ByteArray in a data class compares by reference; the envelope is a value type,
    // so identity is the stored bytes' content.
    override fun equals(other: Any?): Boolean =
        other is DailyPuzzleEntity &&
            gameId == other.gameId &&
            day == other.day &&
            payload.contentEquals(other.payload) &&
            fetchedAtMs == other.fetchedAtMs

    override fun hashCode(): Int {
        var result = gameId.hashCode()
        result = 31 * result + day.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + fetchedAtMs.hashCode()
        return result
    }
}

fun DailyPuzzleEntity.toDomain() = DailyPuzzle(
    gameId = gameId,
    day = day,
    payload = payload,
    fetchedAtMs = fetchedAtMs,
)
