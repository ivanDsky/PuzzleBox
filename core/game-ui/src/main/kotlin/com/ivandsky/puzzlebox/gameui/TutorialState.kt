package com.ivandsky.puzzlebox.gameui

/** Where a [TutorialSession] is. [TutorialHost] draws exactly this. */
sealed interface TutorialState {

    /**
     * Lesson [index] (0-based) of [count] is on the board. Once it is [solved] the same
     * board stays for a short success beat before the next lesson.
     */
    data class Playing(
        val index: Int,
        val count: Int,
        val step: TutorialStep,
        val solved: Boolean = false,
    ) : TutorialState

    /** Every lesson is solved: the closing "You're ready". */
    data object Ready : TutorialState

    /** The player skipped the rest; the caller has already moved on. */
    data object Skipped : TutorialState
}
