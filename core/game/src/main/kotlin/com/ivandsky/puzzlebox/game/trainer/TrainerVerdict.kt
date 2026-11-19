package com.ivandsky.puzzlebox.game.trainer

/**
 * How a snippet's one move was judged against the snippet's target technique (spec A4).
 * The in-a-row count follows: [Right] raises it, [AnotherWay] leaves it, [Wrong] resets it.
 */
enum class TrainerVerdict {
    /** The target technique proves this move on this board. Raises the in-a-row count. */
    Right,

    /** The move fits the solution, but the target technique does not prove it. The count neither rises nor resets. */
    AnotherWay,

    /** The move breaks the solution. Resets the in-a-row count. */
    Wrong,
}
