package com.ivandsky.puzzlebox.play.domain

import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.MatchResultOutbox
import com.ivandsky.puzzlebox.data.match.SelfMatchRepository
import com.ivandsky.puzzlebox.data.match.seatFor
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import kotlinx.coroutines.flow.first

/**
 * Turns a finished duel leg into one result waiting to post (one attempt per leg). Runs after the
 * leg's normal completion went through the first-win policy; it only enqueues, posting is the poster's job.
 * The result is written for the seat this device gives the player: the fixed self seat in a self duel, signed in
 * or out, else the account. A friend duel signed out writes nothing; a leg already queued for the seat keeps its
 * first result. The time is stored as the game reported it — flooring is the outcome's job.
 */
class FinishLeg(
    private val outbox: MatchResultOutbox,
    private val self: SelfMatchRepository,
    private val identity: IdentityService,
    private val clock: () -> Instant,
) {

    suspend operator fun invoke(code: ShareCode, legIndex: Int, finish: LegFinish): FinishResult {
        val seat = self.seatFor(code, identity.current.value?.uid) ?: return FinishResult.SignedOut
        val recorded = outbox.observe(code).first().any { it.result.legIndex == legIndex && it.result.uid == seat }
        if (recorded) return FinishResult.AlreadyRecorded
        val completedAtMs = clock().toEpochMilli()
        val result = when (finish) {
            is LegFinish.Solved ->
                LegResult(seat, legIndex, finish.timeMs, finish.hints, completedAtMs, gaveUp = false, replay = finish.replay)
            is LegFinish.GaveUp ->
                LegResult(seat, legIndex, timeMs = 0, hints = 0, completedAtMs = completedAtMs, gaveUp = true, replay = finish.replay)
        }
        outbox.enqueue(code, result)
        return FinishResult.Queued
    }
}
