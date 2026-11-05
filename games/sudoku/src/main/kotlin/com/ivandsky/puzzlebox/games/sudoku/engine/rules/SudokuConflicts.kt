package com.ivandsky.puzzlebox.games.sudoku.engine.rules

import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard

/** Rule-only checks: duplicates within a unit. The solution is never consulted. */
object SudokuConflicts {

    /** Every duplicated digit per unit, in `geometry.units` order, then ascending digit. */
    fun find(board: SudokuBoard): List<SudokuConflict> {
        val geometry = board.geometry
        var conflicts: MutableList<SudokuConflict>? = null
        for (unit in geometry.units) {
            val cells = geometry.cellsOf(unit)
            var seen = 0
            var duplicated = 0
            for (cell in cells) {
                val digit = board.digitAt(cell)
                if (digit == 0) continue
                val bit = 1 shl (digit - 1)
                if (seen and bit != 0) duplicated = duplicated or bit else seen = seen or bit
            }
            var remaining = duplicated
            while (remaining != 0) {
                val digit = Integer.numberOfTrailingZeros(remaining) + 1
                remaining = remaining and (remaining - 1)
                val holders = cells.filter { board.digitAt(it) == digit }
                (conflicts ?: mutableListOf<SudokuConflict>().also { conflicts = it })
                    .add(SudokuConflict(unit, digit, holders))
            }
        }
        return conflicts ?: emptyList()
    }

    fun cellsIn(conflicts: List<SudokuConflict>): Set<Int> = conflicts.flatMapTo(sortedSetOf()) { it.cells }

    fun isSolved(board: SudokuBoard): Boolean = board.isFull && find(board).isEmpty()
}
