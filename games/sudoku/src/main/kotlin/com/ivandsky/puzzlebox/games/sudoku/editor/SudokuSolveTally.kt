package com.ivandsky.puzzlebox.games.sudoku.editor

/**
 * What one scored solve took (92): how many steps of each [SudokuSolveStep], and [spotting],
 * what it cost to find each of them on the board. The [score] is the two together: the steps
 * by their weights, plus the spotting.
 */
internal class SudokuSolveTally {
    private val counts = IntArray(SudokuSolveStep.entries.size)

    var spotting: Int = 0
        private set

    /** False when the solve stopped with cells still open and no step left. */
    var solved: Boolean = false

    val hardest: SudokuRung? get() = SudokuSolveStep.entries.lastOrNull { counts[it.ordinal] > 0 }?.rung

    val score: Int get() = SudokuSolveStep.entries.sumOf { it.weight * counts[it.ordinal] } + spotting

    fun count(step: SudokuSolveStep): Int = counts[step.ordinal]

    fun add(step: SudokuSolveStep) {
        counts[step.ordinal]++
    }

    /**
     * Adds what one look over the board cost when [moves] of its [open] cells could be placed:
     * the cells looked at in vain for each one found. Nothing when every open cell has a
     * move, the whole board less one when a single cell has, and every open cell when none has.
     */
    fun spot(open: Int, moves: Int) {
        spotting += if (moves == 0) open else (open - moves) / moves
    }
}
