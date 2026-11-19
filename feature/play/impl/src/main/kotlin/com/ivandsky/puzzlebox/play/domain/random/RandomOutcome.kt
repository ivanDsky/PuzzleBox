package com.ivandsky.puzzlebox.play.domain.random

/** What one random pick concluded. */
sealed interface RandomOutcome {
    data class Picked(val pick: RandomPick) : RandomOutcome

    /** The pool is empty after the filters. */
    data object NothingLeft : RandomOutcome

    /** The open budget ran out on days the source could not serve. */
    data object NoneOpened : RandomOutcome

    data object Offline : RandomOutcome

    data object Throttled : RandomOutcome

    /** The community list was refused. */
    data object Refused : RandomOutcome
}
