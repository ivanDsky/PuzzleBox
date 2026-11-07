package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason

/** One ladder step: stand a queen on a cell, or exclude cells, as [unit] proves. */
sealed interface QueensDeduction {
    val rung: QueensRung
    val unit: HintReason             // the row/column/region the reason names
    data class Place(override val rung: QueensRung, override val unit: HintReason, val cell: Int) : QueensDeduction
    data class Exclude(override val rung: QueensRung, override val unit: HintReason, val cells: Set<Int>) : QueensDeduction
}
