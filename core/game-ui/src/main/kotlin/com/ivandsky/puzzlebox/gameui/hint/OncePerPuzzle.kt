package com.ivandsky.puzzlebox.gameui.hint

/**
 * One value per puzzle, resolved once (ADR-0025 decision 3): the step games' solution memo. Only the last puzzle
 * asked about is kept, so a new puzzle replaces it. A null [S] is a resolved answer, not a miss.
 *
 * [get] may run on any thread. [compute] must be deterministic, so two searches racing on a cold memo return the same
 * value and whichever writes last is as good as the other; nothing locks, and a lost race only repeats the work.
 */
class OncePerPuzzle<P : Any, S>(private val compute: (P) -> S) {

    /** Written by a compute block off the main thread, read by later ones on any thread; one pair, so never torn. */
    @Volatile private var resolved: Pair<P, S>? = null

    /** The value for [puzzle] (compared by equality): the memo's, or [compute]d and kept. */
    fun get(puzzle: P): S {
        resolved?.takeIf { it.first == puzzle }?.let { return it.second }
        return compute(puzzle).also { resolved = puzzle to it }
    }
}
