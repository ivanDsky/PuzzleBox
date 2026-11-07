package com.ivandsky.puzzlebox.games.zip.engine

import com.ivandsky.puzzlebox.games.zip.Cell

/** A budgeted [ZipSearch.bounded] run: settled with its paths, or stopped by the budget. */
sealed interface ZipSearchOutcome {
    data class Done(val solutions: List<List<Cell>>) : ZipSearchOutcome // distinct, at most `limit`
    data object Exhausted : ZipSearchOutcome
}
