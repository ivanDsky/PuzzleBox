package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

/** One ladder step: stand a queen on a cell, or exclude cells, as [unit] proves; [unit] also names the rung that found it. */
sealed interface QueensDeduction {
    val unit: HintReason             // the row/column/region the reason names, and the rung
    data class Place(override val unit: HintReason, val cell: Int) : QueensDeduction
    data class Exclude(override val unit: HintReason, val cells: Set<Int>) : QueensDeduction
}
