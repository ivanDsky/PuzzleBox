package com.ivandsky.puzzlebox.data.match

import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.model.SolveReplay
import java.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

/** [authorUid] is set for a friend's puzzle leg, null for a daily. */
data class MatchLeg(val ref: LegRef, val authorUid: String?)

data class Player(val uid: String, val name: String, val photoUrl: String?)

data class Match(
    val code: ShareCode,
    val formatVersion: Int,
    val createdAt: Instant,
    val deadlineHours: Int,
    val challenger: Player,
    val invitedUid: String?,
    val opponent: Player?,
    val legs: List<MatchLeg>,
    val includeFriends: Boolean,
    val cancelled: Boolean,
    val declined: Boolean,
    val rematchOf: ShareCode?,
    val progress: Map<String, Set<Int>>,
) {
    val deadline: Instant get() = createdAt.plusSeconds(deadlineHours.toLong() * SECONDS_PER_HOUR)
    val closesAt: Instant get() = deadline.plus(GRACE.toJavaDuration())

    /** The other player as seen by [uid]; null for a stranger or while no one has claimed the seat. */
    fun opponentOf(uid: String): Player? = when (uid) {
        challenger.uid -> opponent
        opponent?.uid -> challenger
        else -> null
    }

    companion object {
        const val FORMAT_VERSION = 1
        val GRACE = 6.hours
        const val MAX_LEGS = 10
        val DEADLINE_CHOICES = listOf(24, 48)

        private const val SECONDS_PER_HOUR = 3_600L
    }
}

data class MatchDraft(
    val code: ShareCode,
    val deadlineHours: Int,
    val challenger: Player,
    val invitedUid: String?,
    val legs: List<MatchLeg>,
    val includeFriends: Boolean,
    val rematchOf: ShareCode?,
)

data class LegResult(
    val uid: String,
    val legIndex: Int,
    val timeMs: Long,
    val hints: Int,
    val completedAtMs: Long,
    val gaveUp: Boolean,
    /** Advisory only: no verdict reads it. */
    val replay: SolveReplay? = null,
)
