package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.LegFinish
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * A duel leg's completions: a leg solve is a normal completion, tagged with its
 * match. The leg's outbox row is the one record of "this puzzle is taken", so it is queued
 * first and the completion written after it through [inner] (first-win rules, boards and
 * records see it as any solve). A kill between the two keeps the duel result and loses only
 * the completion; a retried write finds the row already queued and writes the completion
 * again. Signed out, nothing is queued and the completion is still written.
 */
internal class LegCompletionRepository(
    private val inner: CompletionRepository,
    private val code: ShareCode,
    private val legIndex: Int,
    private val finishLeg: FinishLeg,
) : CompletionRepository by inner {

    override suspend fun onCompleted(record: CompletionRecord) {
        finishLeg(code, legIndex, LegFinish.Solved(record.time, record.hints))
        inner.onCompleted(record.copy(matchCode = code.value))
    }
}

/** The completions [slot]'s play writes to: a duel leg's go through [LegCompletionRepository], a
 *  day's or friend's puzzle's are this repository as is. A leg whose code doesn't parse never opens
 *  a board (SlotOpening), so it never completes. */
internal fun CompletionRepository.forSlot(slot: PlaySlot, finishLeg: FinishLeg): CompletionRepository {
    val leg = slot as? PlaySlot.Leg ?: return this
    val code = ShareCode.parse(leg.matchCode) ?: return this
    return LegCompletionRepository(this, code, leg.legIndex, finishLeg)
}
