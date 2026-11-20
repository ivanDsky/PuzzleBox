package com.ivandsky.puzzlebox.game.trainer

/**
 * How one practice turn went for the trained technique (spec §2.2, A5). Only turns where the trained
 * technique was the easiest rung that applied are judged this way; other turns never touch the count.
 */
enum class TrainerVerdict {
    /** The player made a move of the trained technique's step. Raises the in-a-row count. */
    Right,

    /** The player made another move (it broke the solution or skipped the trained step); it was undone. Resets the count. */
    Wrong,

    /** The player asked to be shown the trained step. Resets the count. */
    ShowMe,
}
