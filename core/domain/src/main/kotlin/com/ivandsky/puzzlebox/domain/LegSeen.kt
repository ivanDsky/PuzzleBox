package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.match.LegResult

/** What the viewer knows of the opponent's leg: nothing yet, that it is done (time sealed, A7), or their result. */
sealed interface LegSeen {
    data class Revealed(val result: LegResult) : LegSeen

    data object Played : LegSeen

    data object NotPlayed : LegSeen
}
