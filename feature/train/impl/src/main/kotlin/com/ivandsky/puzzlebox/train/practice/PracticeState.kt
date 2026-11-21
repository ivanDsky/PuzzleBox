package com.ivandsky.puzzlebox.train.practice

import com.ivandsky.puzzlebox.gameui.trainer.PracticeBoard

/**
 * Where a technique's practice stands: a board being found or a puzzle loading, nothing to practice on, waiting for a
 * puzzle to be fetched, unable to load one, on a board, or on the result of a finished chapter. What the board itself
 * says (the last move's outcome, a finished board) is its own [PracticeBoard.status], not part of this state.
 */
internal sealed interface PracticeState {

    /** A board is being cut from the puzzles on the device, or one tapped puzzle is loading; taps are ignored. */
    data object Loading : PracticeState

    /** The technique's index names no puzzle at all, so there is nothing to ask and nothing to fetch. */
    data object Empty : PracticeState

    /** Nothing on the device has a point where the technique is the easiest move, but its index names puzzles: one tap fetches the first. */
    data object NeedsConnection : PracticeState

    /**
     * The last tap did not give a puzzle. [alternative] is the number of a puzzle already on the device that the next
     * tap opens, with no connection.
     */
    data class CantLoad(val alternative: Int) : PracticeState

    /** [board] is up. Each board is its own state (it compares by identity), so a new board always replaces the last. */
    class Practicing(val board: PracticeBoard) : PracticeState

    /** A chapter's last board is done: the [result] page, whose buttons say where to go (the next chapter, again, or back). */
    data class Result(val result: ChapterResult) : PracticeState
}
