package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.ScoreModel

/**
 * Emits the normalized completion (ADR-0004), zip ZipScoreModel conventions: the stats
 * slot v1 carries the variant + queens-per-unit (analytics, never indexed); indexed time
 * and hints land with the session timer (queens-02+). Ref-less boards are not
 * record-eligible (ADR-0004: no identity, no record). The skeleton session carries no
 * completion stamp yet, so completion() returns null until the session tickets land —
 * null is the ADR-0004 "not record-eligible" outcome, not a stub path.
 */
class QueensScoreModel : ScoreModel<QueensSession> {

    override fun completion(session: QueensSession): CompletionRecord? = null
}
