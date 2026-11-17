package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.SelfSeats
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant

/** A new self duel and past you's frozen result, one per leg, as [com.ivandsky.puzzlebox.data.match.SelfMatchRepository.save] takes them. */
data class SelfMatchDraft(val match: Match, val past: List<LegResult>)

/**
 * Pure: turns drawn [MatchLeg]s and each puzzle's record (its first win, never best or latest) into the self duel
 * and past you's results. `records[i]` is the record of `legs[i]`.
 */
fun buildSelfMatch(
    code: ShareCode,
    createdAt: Instant,
    includeFriends: Boolean,
    legs: List<MatchLeg>,
    records: List<CompletionRecord>,
): SelfMatchDraft {
    require(legs.size == records.size) { "one record per leg" }
    val past = records.mapIndexed { index, record ->
        LegResult(
            uid = SelfSeats.PAST,
            legIndex = index,
            timeMs = record.time,
            hints = record.hints,
            completedAtMs = (record.firstCompletedAt ?: createdAt).toEpochMilli(),
            gaveUp = false,
        )
    }
    val match = Match(
        code = code,
        formatVersion = Match.FORMAT_VERSION,
        createdAt = createdAt,
        deadlineHours = SelfSeats.NO_DEADLINE_HOURS,
        challenger = Player(SelfSeats.ME, "You", photoUrl = null),
        invitedUid = null,
        opponent = Player(SelfSeats.PAST, SelfSeats.PAST_NAME, photoUrl = null),
        legs = legs,
        includeFriends = includeFriends,
        cancelled = false,
        declined = false,
        rematchOf = null,
        progress = mapOf(SelfSeats.PAST to legs.indices.toSet(), SelfSeats.ME to emptySet()),
    )
    return SelfMatchDraft(match, past)
}
