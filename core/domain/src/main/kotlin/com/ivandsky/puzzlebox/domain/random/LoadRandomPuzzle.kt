package com.ivandsky.puzzlebox.domain.random

/** The one entry point for a random pick: routes the [RandomFilter] to its pool's use case. */
class LoadRandomPuzzle(
    private val day: LoadRandomDay,
    private val shared: LoadRandomShared,
) {
    suspend operator fun invoke(filter: RandomFilter, last: RandomPick?): RandomOutcome = when (filter) {
        is RandomFilter.Archive -> day(filter, last)
        is RandomFilter.Community -> shared(filter, last)
    }
}
