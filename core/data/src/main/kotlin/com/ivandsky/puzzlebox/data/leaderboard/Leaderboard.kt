package com.ivandsky.puzzlebox.data.leaderboard

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.Flow

/**
 * One board = one official daily puzzle (A5). [gameId] + ISO day; the daily is exactly
 * one puzzle per day, so no puzzle number belongs on the board identity — the full
 * `zip/<day>/<n>` ref travels on the entries instead (and is re-validated by Firestore
 * rules against the board id).
 */
data class LeaderboardRef(val gameId: String, val day: LocalDate) {

    /** Stable board id: Firestore path segment and cache key (`zip_2026-10-31`). */
    val boardId: String get() = "$gameId$BOARD_ID_SEPARATOR$day"

    companion object {
        const val BOARD_ID_SEPARATOR = "_"

        /** Inverse of [LeaderboardRef.boardId]; null for ids this contract never authored. */
        fun fromBoardId(id: String): LeaderboardRef? {
            val parts = id.split(BOARD_ID_SEPARATOR)
            if (parts.size != 2) return null
            val day = try {
                LocalDate.parse(parts[1])
            } catch (_: DateTimeParseException) {
                return null
            }
            return LeaderboardRef(parts[0], day)
        }

        /**
         * The board a `gameId/<ISO-day>/<n>` puzzle-ref key belongs to. Keys without a
         * puzzle number are not official-daily-shaped (custom boards, Phase 2) and are
         * never leaderboard-eligible (A5) — null.
         */
        fun fromPuzzleRefKey(key: String): LeaderboardRef? {
            val parts = key.split('/')
            if (parts.size != 3) return null
            val day = try {
                LocalDate.parse(parts[1])
            } catch (_: DateTimeParseException) {
                return null
            }
            if (parts[2].toIntOrNull() == null || parts[2].toInt() <= 0) return null
            return LeaderboardRef(parts[0], day)
        }
    }
}

/** One leaderboard row: exactly what Firestore stores (and the rules sanity-validate). */
data class LeaderboardEntry(
    val uid: String,
    val displayName: String,
    /** The completions store's ref key: `gameId/<ISO-day>/<n>` for a daily, `custom/<gameId>/<puzzleId>` for a friend puzzle. */
    val puzzleRef: String,
    /** A3 score inputs; ranking is (timeMs, then hints), lower is better, mistakes free. */
    val timeMs: Long,
    val hints: Int,
    val completedAtMs: Long,
)

/**
 * Versioned snapshot envelope (A19/ADR-0005): every cached board self-describes its
 * schema version, so a backend swap or schema evolution never strands a cached snapshot.
 */
data class LeaderboardSnapshot(
    val board: LeaderboardRef,
    /** Sorted by the board's [RankOrder.entryComparator] — rank is list position. */
    val entries: List<LeaderboardEntry>,
    val formatVersion: Int = FORMAT_VERSION,
    /** An answer from the device cache, not yet confirmed by the server; an empty one proves nothing about the board. */
    val fromCache: Boolean = false,
) {
    companion object {
        const val FORMAT_VERSION = 1
    }
}

sealed interface SubmitResult {
    /** Entry written. */
    data class Ok(val entry: LeaderboardEntry) : SubmitResult

    /** This (uid, puzzleRef) already has an entry — the first write stands (A4/A5). */
    data class Deduped(val existing: LeaderboardEntry?) : SubmitResult

    data class Err(val error: SubmitError) : SubmitResult
}

sealed interface SubmitError {
    /** Offline, backend unreachable, or signed out — local-only posture (A7). */
    data class Unavailable(val message: String?) : SubmitError

    /** Firestore refused the write (rules, bad payload). */
    data class Rejected(val message: String?) : SubmitError

    /** Anything else; report and log. */
    data class Unknown(val cause: Throwable) : SubmitError
}

/**
 * Leaderboard read/submit seam (ticket-16 design §2). Implementations live in backend
 * modules; snapshots are versioned envelopes (A19).
 */
interface LeaderboardRepository {

    /**
     * Live board ranked by [order]; null = not observable right now (soft unavailable, A14).
     * [order] MUST be the board's game's rank order; `ObserveLeaderboard` and
     * `ObserveRankWindow` are the production callers.
     */
    fun observe(board: LeaderboardRef, order: RankOrder): Flow<LeaderboardSnapshot?>

    /**
     * The whole board once, ranked by [order], with its N, read from the server only: null when
     * signed out, offline or the read failed, never a cached or empty stand-in, so a non-null
     * page is safe to keep for good. N costs a second read only when the page is full
     * ([BoardPage.PAGE_SIZE]).
     */
    suspend fun fetch(board: LeaderboardRef, order: RankOrder): BoardPage?

    /** N alone — for an open board whose live page is at the cap; null when signed out or failed. */
    suspend fun count(board: LeaderboardRef): Int?

    suspend fun submit(entry: LeaderboardEntry): SubmitResult
}

/** The one entry ranking rule: [RankOrder.comparator] over the entry's (timeMs, hints). */
fun RankOrder.entryComparator(): Comparator<LeaderboardEntry> =
    compareBy(comparator) { BestScore(it.timeMs, it.hints) }
