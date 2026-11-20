package com.ivandsky.puzzlebox.gameui.trainer

import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight

/**
 * The outcome a [PracticeBoard] reports after each move, for the status band and the board's Spotlight layer
 * ([PracticeSpotlight]). Every [Spotlight] here is the game's hint mapper's own, so the why reads like the hint.
 * [Easier] and [Shown] are explanations: while one stands, the board takes no move until [PracticeBoard.gotIt].
 */
sealed interface PracticeStatus {
    /** Waiting for a move: the band reads "Find the next move". */
    data object FindNextMove : PracticeStatus

    /**
     * The move is part of a right step that has more squares to make; [left] is how many (at least 1). Right
     * comes when the last of them is made. [trained] when the step's rung is the trained technique.
     */
    data class KeepGoing(val left: Int, val trained: Boolean) : PracticeStatus

    /**
     * The move finished a step of the easiest rung that applies; [spotlight] is that step, which the board only
     * flashes (no callout, play goes on). [trained] when that rung is the trained technique.
     */
    data class Right(val spotlight: Spotlight, val trained: Boolean) : PracticeStatus

    /**
     * The move fits the solution, but an easier technique applied; the move was undone. [technique] is that
     * technique's catalog name, and [spotlight] shows its step. The board waits for [PracticeBoard.gotIt].
     */
    data class Easier(val technique: String, val spotlight: Spotlight) : PracticeStatus

    /** The move broke the solution and was undone, leaving nothing behind; [why] is one plain line. */
    data class Wrong(val why: String) : PracticeStatus

    /** Show me made the easiest step; [spotlight] shows it. The board waits for [PracticeBoard.gotIt]. */
    data class Shown(val spotlight: Spotlight) : PracticeStatus

    /** The board is solved. */
    data object Solved : PracticeStatus
}
