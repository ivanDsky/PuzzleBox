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
     * comes when the last of them is made. [trained] when the step's rung is the trained technique. [unit] is
     * what the game calls one of them, singular ("square", "note"), and the band says "N more squares"; null when the
     * game counts something that has no one plain name, and the band says "N more in this step".
     */
    data class KeepGoing(val left: Int, val trained: Boolean, val unit: String? = null) : PracticeStatus

    /**
     * The move finished a step of the easiest rung that applies; [spotlight] is that step, which the board only
     * flashes (no callout, play goes on). [trained] when that rung is the trained technique. [technique] is the
     * catalog name of that rung when it is not the trained one (null when unknown), for the band's "Fits" line.
     */
    data class Right(val spotlight: Spotlight, val trained: Boolean, val technique: String? = null) : PracticeStatus

    /**
     * The move fits the solution, but an easier technique applied; the move was undone. [technique] is that
     * technique's catalog name, and [spotlight] shows its step. The board waits for [PracticeBoard.gotIt].
     */
    data class Easier(val technique: String, val spotlight: Spotlight) : PracticeStatus

    /**
     * The move broke the solution and was undone, leaving nothing behind; [why] is one plain line. [attempt] counts
     * the undone moves of the board, so two moves undone for the same reason in a row are still two statuses
     * (a [kotlinx.coroutines.flow.StateFlow] drops an equal value). A game must make it unique per undone move (count up
     * from 1); the chapter's "undone" tally counts every Wrong it sees, so a game that leaves it 0 loses repeats.
     */
    data class Wrong(val why: String, val attempt: Int = 0) : PracticeStatus

    /** Show me made the easiest step; [spotlight] shows it. The board waits for [PracticeBoard.gotIt]. */
    data class Shown(val spotlight: Spotlight) : PracticeStatus

    /**
     * The board's part is over and it takes no more moves ([com.ivandsky.puzzlebox.game.trainer.BoardKind]): a
     * focus board's trained run ended, a review board needs a technique not yet learned, or the puzzle is solved.
     * [last] is the step whose move ended it, which the board flashes; null when Got it ended it (after Show me
     * made the last step) or the board opened with nothing to ask. [needsOtherTechnique] when the board ended
     * with the puzzle unsolved because its next move needs a technique the player has not learned.
     */
    data class Done(val last: Spotlight?, val needsOtherTechnique: Boolean = false) : PracticeStatus
}
