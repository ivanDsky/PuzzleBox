package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.MatchResultOutbox
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import kotlinx.coroutines.flow.first

/**
 * Turns a finished duel leg into one result waiting to post (spec A22: one attempt per leg). Runs after the
 * leg's normal completion went through the first-win policy; it only enqueues, posting is the poster's job.
 * Signed out writes nothing; a leg already queued for this player keeps its first result. The time is stored
 * as the game reported it — flooring is the outcome's job.
 */
class FinishLeg(
    private val outbox: MatchResultOutbox,
    private val identity: IdentityService,
    private val clock: () -> Instant,
) {

    suspend operator fun invoke(code: ShareCode, legIndex: Int, finish: LegFinish): FinishResult {
        val uid = identity.current.value?.uid ?: return FinishResult.SignedOut
        val recorded = outbox.observe(code).first().any { it.result.legIndex == legIndex && it.result.uid == uid }
        if (recorded) return FinishResult.AlreadyRecorded
        val completedAtMs = clock().toEpochMilli()
        val result = when (finish) {
            is LegFinish.Solved -> LegResult(uid, legIndex, finish.timeMs, finish.hints, completedAtMs, gaveUp = false)
            LegFinish.GaveUp -> LegResult(uid, legIndex, timeMs = 0, hints = 0, completedAtMs = completedAtMs, gaveUp = true)
        }
        outbox.enqueue(code, result)
        return FinishResult.Queued
    }
}
