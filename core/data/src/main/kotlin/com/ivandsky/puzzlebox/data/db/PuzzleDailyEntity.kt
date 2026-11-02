package com.ivandsky.puzzlebox.data.db

import androidx.room.Entity
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef

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

/** Cache read: decode through the owning game's definition — one decode path for every reader. */
fun PuzzleDailyEntity.toPuzzle(): Puzzle? = ZipDefinition.decode(payload).getOrNull()

/** Cache write: identity + envelope in one row (the wire carries no date; ref comes from the fetch day). */
fun Puzzle.toEntity(
    ref: ZipPuzzleRef,
    fetchedAtMs: Long,
): PuzzleDailyEntity = PuzzleDailyEntity(
    gameId = gameId.value,
    day = ref.date.toString(),
    puzzleNumber = ref.puzzleNumber,
    variant = variant.value,
    formatVersion = formatVersion,
    payload = payload,
    fetchedAtMs = fetchedAtMs,
)

/** Tango row identity (ticket 03): the same puzzle_dailies store, Tango decode path. */
fun Puzzle.toTangoEntity(
    ref: com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef,
    fetchedAtMs: Long,
): PuzzleDailyEntity = PuzzleDailyEntity(
    gameId = gameId.value,
    day = ref.date.toString(),
    puzzleNumber = ref.puzzleNumber,
    variant = variant.value,
    formatVersion = formatVersion,
    payload = payload,
    fetchedAtMs = fetchedAtMs,
)

/** Cache read: decode through the owning game's definition — one decode path for every reader. */
fun PuzzleDailyEntity.toTangoPuzzle(): Puzzle? = TangoDefinition.decode(payload).getOrNull()
