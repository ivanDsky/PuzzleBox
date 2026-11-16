package com.ivandsky.puzzlebox.game.editor

import kotlin.math.abs

/**
 * What a completer aiming for [target] returns (90): the board at the target, else the closest one
 * it found, and [Completion.GaveUp] only when it found none. A completer [offer]s every unique board
 * it finds with its scored level and stops once an offer is a hit. Closest is the fewest levels
 * away; of two equally far the first found stays, and a board with no level is the farthest.
 * A null [target] is "any": the first board is a hit.
 */
class ClosestCompletion<D : Any>(private val target: Difficulty.Level?) {
    private var best: D? = null
    private var bestGap = Int.MAX_VALUE

    /** Takes [draft], a unique board graded [level] (null: ungraded). True when it is at the target, so the search can stop. */
    fun offer(draft: D, level: Difficulty.Level?): Boolean {
        val gap = when {
            target == null -> 0
            level == null -> Difficulty.Level.entries.size
            else -> abs(level.ordinal - target.ordinal)
        }
        if (gap < bestGap) {
            best = draft
            bestGap = gap
        }
        return gap == 0
    }

    /** The closest board offered as [Completion.Done]; [Completion.GaveUp] when none was. */
    fun completion(): Completion<D> = best?.let { Completion.Done(it) } ?: Completion.GaveUp
}
