package com.ivandsky.puzzlebox.domain

/**
 * A friend's puzzle as From friends reads it (spec A19), in precedence order: the first case
 * that holds wins, so a solved puzzle its author took down still reads [Solved].
 */
sealed interface FriendStatus {
    /** Its game isn't registered in this build, or its board is in a newer format (A24). */
    data object NeedsUpdate : FriendStatus

    /** A first solve is on record, in [timeMs]. */
    data class Solved(val timeMs: Long) : FriendStatus

    /** Its author withdrew it and it isn't solved; the local copy still plays. */
    data object TakenDown : FriendStatus

    /** A saved play, at its elapsed time when last saved. */
    data class InProgress(val elapsedMs: Long) : FriendStatus

    /** Opened, never played. */
    data object New : FriendStatus
}
