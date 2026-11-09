package com.ivandsky.puzzlebox.data.match

import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** How this device came to know a duel: a link opened but not claimed, or a seat taken. */
enum class LocalRole { Invite, Participant }

/** The device's own memory of one duel; the server knows the players, only the device knows the rest. */
data class LocalMatch(
    val code: ShareCode,
    val role: LocalRole,
    val opponentName: String?,
    val openedAt: Instant,
    val revealSeenAt: Instant?,
    val dismissed: Boolean,
)

/** Local per-duel state: invite opened, reveal seen, invite dismissed. */
interface MatchLocalRepository {
    /** Every row, dismissed ones included (flag set), newest [LocalMatch.openedAt] first. */
    fun observeAll(): Flow<List<LocalMatch>>

    /**
     * Upsert. A new row stamps `openedAt` now. An existing row keeps `openedAt` and `dismissed`,
     * never downgrades [LocalRole.Participant] to [LocalRole.Invite], and takes [opponentName]
     * only when non-null.
     */
    suspend fun remember(code: ShareCode, role: LocalRole, opponentName: String?)

    /** Sets the reveal-seen time once; later calls and unknown codes change nothing. */
    suspend fun markRevealSeen(code: ShareCode, at: Instant)

    /** Hides the invite; no-op for an unknown code. */
    suspend fun dismiss(code: ShareCode)

    /** matchCode to opponent name, for rows that have a name (history rows). */
    fun opponentNames(): Flow<Map<String, String>>
}
