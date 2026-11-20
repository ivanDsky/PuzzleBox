package com.ivandsky.puzzlebox.gameui.trainer

import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight

/**
 * The outcome a [PracticeBoard] reports after each move, for the status band and the Spotlight over the
 * board. Every [Spotlight] here is the game's hint mapper's own, so the why reads like the hint.
 */
sealed interface PracticeStatus {
    /** Waiting for a move: the band reads "Find the next move". */
    data object FindNextMove : PracticeStatus

    /**
     * The move belongs to a step of the easiest rung that applies; [spotlight] shows that step (the one the
     * move belongs to). [trained] when that rung is the trained technique.
     */
    data class Right(val spotlight: Spotlight, val trained: Boolean) : PracticeStatus

    /**
     * The move fits the solution, but an easier technique applied; the move was undone. [technique] is that
     * technique's catalog name, and [spotlight] shows its step.
     */
    data class Easier(val technique: String, val spotlight: Spotlight) : PracticeStatus

    /** The move broke the solution and was undone, leaving nothing behind; [why] is one plain line. */
    data class Wrong(val why: String) : PracticeStatus

    /** Show me made the easiest step; [spotlight] shows it. */
    data class Shown(val spotlight: Spotlight) : PracticeStatus

    /** The board is solved. */
    data object Solved : PracticeStatus
}
