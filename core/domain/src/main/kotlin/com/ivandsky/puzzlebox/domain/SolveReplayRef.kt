package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.model.ShareCode

/** What to open. Never bytes, never in a NavKey: a reference is cheap to pass and bytes are not. */
sealed interface SolveReplayRef {
    /** The record solve, the first win of the puzzle. */
    class Record(val puzzle: PuzzleRef) : SolveReplayRef

    /** The newest solve of the puzzle. */
    class Latest(val puzzle: PuzzleRef) : SolveReplayRef

    class MyLeg(val code: ShareCode, val legIndex: Int) : SolveReplayRef

    /** Online only: the opponent's leg exists only on the server. */
    class TheirLeg(val code: ShareCode, val legIndex: Int) : SolveReplayRef
}
