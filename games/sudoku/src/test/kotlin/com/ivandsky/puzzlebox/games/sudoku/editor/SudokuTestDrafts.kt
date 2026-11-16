package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import kotlin.random.Random

/** Seeded partial drafts for the completer's and the random fill's tests and measurement. */
internal object SudokuTestDrafts {

    /** [grid] with [kept] cells drawn by [random] left as givens and every other cell opened: it has a solution. */
    fun cut(geometry: SudokuGeometry, grid: List<Int>, kept: Int, random: Random): SudokuDraft {
        val keep = grid.indices.shuffled(random).take(kept).toSet()
        return SudokuDraft(geometry.size, grid.mapIndexed { idx, digit -> if (idx in keep) digit else 0 })
    }

    /**
     * [count] digits dropped on cells drawn by [random], each a digit no peer holds yet, as an
     * author placing digits with no grid in mind would: no two clash, but no grid may agree
     * with them all. A drawn cell every digit clashes on stays open.
     */
    fun scattered(geometry: SudokuGeometry, count: Int, random: Random): SudokuDraft {
        val givens = IntArray(geometry.cellCount)
        for (cell in (0 until geometry.cellCount).shuffled(random).take(count)) {
            val taken = geometry.peersOf(cell).map { givens[it] }.toSet()
            val free = (1..geometry.size).filter { it !in taken }
            if (free.isNotEmpty()) givens[cell] = free[random.nextInt(free.size)]
        }
        return SudokuDraft(geometry.size, givens.toList())
    }
}
