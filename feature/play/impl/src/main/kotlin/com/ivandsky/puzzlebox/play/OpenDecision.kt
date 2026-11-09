package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.play.api.ResultKey

/** What opening a [com.ivandsky.puzzlebox.play.api.GameKey] shows, as far as the store knows without a read. */
internal sealed interface OpenDecision {
    /** The board: a live or unsolved day. */
    data object Open : OpenDecision

    /** The day's result. */
    data class Finished(val result: ResultKey) : OpenDecision

    /** A solved day with no live play: [result] holds unless a saved replay row exists (a read). */
    data class Check(val result: ResultKey) : OpenDecision

    /** The solved-day snapshot has not loaded yet. */
    data object Cold : OpenDecision
}
