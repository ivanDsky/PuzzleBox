package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.games.queens.QueensReplayMove
import com.ivandsky.puzzlebox.games.queens.QueensSession

/**
 * A practice board's say over the moves of a [QueensBoardState] (spec §2.2). The state's own gestures run as
 * ever; the judge only sees each touch and each move before it is stored, so daily play, which has no judge,
 * behaves as before.
 */
internal interface QueensMoveJudge {

    /**
     * A touch on [idx] is about to be handled: a tap, or the first cell of a [drag]. It runs before the board
     * reads its session, so the judge may [QueensBoardState.restore] a move it was holding.
     */
    fun input(idx: Int, drag: Boolean)

    /**
     * The session to store for [move], which took [before] to [after]: [after] to keep the move, [before] (the very
     * same instance) to refuse it, or any other session to put in its place.
     */
    fun judged(before: QueensSession, after: QueensSession, move: QueensReplayMove): QueensSession
}
