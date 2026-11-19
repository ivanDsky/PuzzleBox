package com.ivandsky.puzzlebox.compete.domain

import com.ivandsky.puzzlebox.domain.MatchState
import java.time.Instant

/**
 * What the duel screen reads for one code: the [match] state, whether the viewer already has a completion of a
 * puzzle of the duel ([solvedBefore]; false when no match could be read) and the elapsed time of the viewer's saved
 * plays of this duel by puzzle index ([elapsedMs]). [firstSolvedAt] is when the viewer first solved each puzzle they
 * have a record for, by `PuzzleRefKeys` string: the date a self duel's past you set its score, never the score.
 */
data class DuelSnapshot(
    val match: MatchState,
    val solvedBefore: Boolean,
    val elapsedMs: Map<Int, Long>,
    val firstSolvedAt: Map<String, Instant> = emptyMap(),
)
