package com.ivandsky.puzzlebox.domain

/**
 * What a duel row asks of the player; the first three need them (see [MyDuels.needYou]). [Finished] asks nothing:
 * the duel is over and its reveal was seen.
 */
sealed interface DuelRowKind {
    data object Invited : DuelRowKind

    data object YourMove : DuelRowKind

    data object RevealReady : DuelRowKind

    data object Waiting : DuelRowKind

    data object Unclaimed : DuelRowKind

    /**
     * A duel whose reveal was seen on this device. [myScore] and [theirScore] are the puzzles each side won as that
     * reveal showed them; both are null for a reveal seen before the scores were kept.
     */
    data class Finished(val myScore: Int?, val theirScore: Int?) : DuelRowKind
}
