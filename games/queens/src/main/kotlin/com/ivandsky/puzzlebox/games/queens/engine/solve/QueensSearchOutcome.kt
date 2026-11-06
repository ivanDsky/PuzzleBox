package com.ivandsky.puzzlebox.games.queens.engine.solve

import com.ivandsky.puzzlebox.games.queens.Cell

/** A budgeted [QueensSearch.bounded] run: settled with its solutions, or stopped by the budget. */
sealed interface QueensSearchOutcome {
    data class Done(val solutions: List<List<Cell>>) : QueensSearchOutcome
    data object Exhausted : QueensSearchOutcome
}
