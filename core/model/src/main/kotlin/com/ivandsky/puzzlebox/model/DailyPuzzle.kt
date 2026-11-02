package com.ivandsky.puzzlebox.model

/**
 * One cached official daily, as an opaque envelope: identity (gameId + day), the raw
 * fetched payload bytes, and when it arrived. Nothing game-specific (size, cells, …)
 * is parsed into the shared schema; each game decodes its own payload. Lives in
 * :core:model so the repository contract (:core:data) and game/feature consumers share
 * one definition instead of stringly-typing across the seam.
 */
data class DailyPuzzle(
    val gameId: String,
    val day: String,
    val payload: ByteArray,
    val fetchedAtMs: Long,
) {
    // WHY: ByteArray in a data class compares by reference; the envelope is a value type,
    // so identity is the wire bytes' content.
    override fun equals(other: Any?): Boolean =
        other is DailyPuzzle &&
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
