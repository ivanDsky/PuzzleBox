package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.games.strands.Coord

/** What one input does to the [Trace]: the controller applies it ([StrandsPlayController.onTrace]). */
sealed interface TraceStep {
    data class Updated(val trace: Trace) : TraceStep

    /** The player committed [path]; the controller judges it through StrandsRules. */
    data class Submit(val path: List<Coord>) : TraceStep

    data object Cleared : TraceStep
}
