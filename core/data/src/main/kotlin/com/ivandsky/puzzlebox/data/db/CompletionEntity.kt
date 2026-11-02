package com.ivandsky.puzzlebox.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import java.time.Instant
import java.time.LocalDate

/**
 * Normalized record identity (ADR-0004; binding ticket-12 contract): `gameId +
 * day/puzzleNumber` encoded as one stable string key — `zip/2026-10-31/592`, or
 * `zip/2026-10-31` for a ref without a puzzle number. Identity never rides the codec
 * bytes (A19): the key is derived from the [PuzzleRef] the game's ScoreModel emits.
 */
object PuzzleRefKeys {

    /** Stable string form of the composite identity; [decode] is its only inverse. */
    fun encode(ref: PuzzleRef): String = when (ref) {
        is ZipPuzzleRef -> keyed(ZipDefinition.id.value, ref.date, ref.puzzleNumber)
        // Tango ships its ref type together with this registration (ADR-0004
        // integration convention — same line Zip's onboarding added).
        is TangoPuzzleRef -> keyed(TangoDefinition.id.value, ref.date, ref.puzzleNumber)
        // A game ships its own ref type only together with this registration — same
        // integration convention as Puzzle.toEntity taking ZipPuzzleRef.
        else -> error("Unregistered PuzzleRef cannot key a completion: $ref")
    }

    /** Inverse of [encode]; null for keys no registered game authored (never reinterpreted). */
    fun decode(key: String): PuzzleRef? {
        val parts = key.split('/')
        val gameId = parts.getOrNull(0) ?: return null
        val date = parts.getOrNull(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return null
        val number = when (parts.size) {
            2 -> null
            3 -> parts[2].toIntOrNull() ?: return null
            else -> return null
        }
        return when (gameId) {
            ZipDefinition.id.value -> ZipPuzzleRef(date, number)
            TangoDefinition.id.value -> TangoPuzzleRef(date, number)
            else -> null
        }
    }

    private fun keyed(gameId: String, date: LocalDate, puzzleNumber: Int?): String =
        buildString {
            append(gameId)
            append('/')
            append(date)
            puzzleNumber?.let { append('/').append(it) }
        }
}

/**
 * The completions store (schema v3, ticket 15): every in-app completion of a puzzleRef is
 * kept — exactly one row per ref carries isRecord=1 (A4 first-win-is-record, enforced by
 * FirstWinPolicy), later plays are replay rows kept for stats and never mutate the record
 * (A5: only records are leaderboard-eligible). [statsBlob]/[statsVersion] are the A19
 * self-describing per-game stats slot (ADR-0004): opaque to core, never indexed; promoting
 * a stat to a column is an explicit one-time migration.
 */
@Entity(
    tableName = "completions",
    indices = [Index("puzzleRef")],
)
data class CompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val puzzleRef: String,
    /** A3 score inputs, stored exactly as emitted; lower is better, mistakes stay in stats. */
    val timeMs: Long,
    val hints: Int,
    val statsVersion: Int,
    val statsBlob: String,
    /** When THIS completion happened; on the record row this is the first-win instant. */
    val completedAtMs: Long,
    /** A4 stamp — set only on the record row; replay rows keep null, as emitted. */
    val firstCompletedAtMs: Long?,
    val isRecord: Boolean,
)

/** Cache read: decode through the composite-identity key — one decode path for every reader. */
fun CompletionEntity.toModel(): CompletionRecord? {
    val ref = PuzzleRefKeys.decode(puzzleRef) ?: return null
    return CompletionRecord(
        puzzleRef = ref,
        time = timeMs,
        hints = hints,
        stats = RecordStats(schemaVersion = statsVersion, blob = statsBlob),
        firstCompletedAt = firstCompletedAtMs?.let(Instant::ofEpochMilli),
        isRecord = isRecord,
    )
}
