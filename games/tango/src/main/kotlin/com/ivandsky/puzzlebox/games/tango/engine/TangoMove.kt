package com.ivandsky.puzzlebox.games.tango.engine

import com.ivandsky.puzzlebox.game.Move
import com.ivandsky.puzzlebox.game.MoveVerdict
import com.ivandsky.puzzlebox.games.tango.TangoCell

/** Tango move (F1 move marker): place a value, or `value = null` to clear a player cell. */
data class TangoMove(val idx: Int, val value: TangoCell?) : Move

/** Tango validation outcome (F1 verdict marker): accepted, or rejected with the rule named (spec §2). */
sealed interface TangoVerdict : MoveVerdict {
    data object Valid : TangoVerdict

    /** [reason] names the violated constraint class; [idx] is the offending cell, null for foreign moves. */
    data class Rejected(val reason: Reason, val idx: Int? = null) : TangoVerdict

    enum class Reason {
        /** The move was authored by another game. */
        ForeignMove,

        /** The target cell is an immutable given (A3). */
        LockedGiven,

        /** Three identical cells in a row or column. */
        ThreeInARow,

        /** More than n/2 suns or moons in a row or column. */
        CountExceeded,

        /** An adjacent `=`/`×` marker is violated. */
        MarkerConflict,
    }
}

