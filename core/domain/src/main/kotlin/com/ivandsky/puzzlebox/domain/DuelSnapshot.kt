package com.ivandsky.puzzlebox.domain

/**
 * What the duel screen reads for one code: the [match] state, whether the viewer already has a completion of a
 * puzzle of the duel ([solvedBefore]; false when no match could be read) and the elapsed time of the viewer's saved
 * plays of this duel by puzzle index ([elapsedMs]).
 */
data class DuelSnapshot(
    val match: MatchState,
    val solvedBefore: Boolean,
    val elapsedMs: Map<Int, Long>,
)
