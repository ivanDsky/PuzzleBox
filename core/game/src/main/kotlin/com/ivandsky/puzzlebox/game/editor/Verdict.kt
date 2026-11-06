package com.ivandsky.puzzlebox.game.editor

/**
 * What analysis says about a draft board (spec A7): the verdict pill's state. [Checking] is
 * produced only by the UI pipeline, never by [GameEditor.analyze].
 */
sealed interface Verdict {
    /** The board cannot be solved yet for a game-defined [reason] (unpainted cells, region count). */
    data class Incomplete(val reason: IncompleteReason) : Verdict
    data object Checking : Verdict
    data class Unique(val solution: Solution) : Verdict
    data class Multiple(val solutionA: Solution, val solutionB: Solution, val differingCells: Set<Int>) : Verdict
    data class None(val conflicts: Set<Int>) : Verdict

    /** The budget ran out before the search settled. */
    data object Undetermined : Verdict
}
