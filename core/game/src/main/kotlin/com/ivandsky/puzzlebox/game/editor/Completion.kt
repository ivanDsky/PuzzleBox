package com.ivandsky.puzzlebox.game.editor

/** [DraftCompleter.complete]'s answer. */
sealed interface Completion<out D> {
    data class Done<out D>(val draft: D) : Completion<D>

    /** The budget ran out or no completion was found. */
    data object GaveUp : Completion<Nothing>
}
