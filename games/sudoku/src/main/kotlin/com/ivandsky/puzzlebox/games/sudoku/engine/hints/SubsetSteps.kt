package com.ivandsky.puzzlebox.games.sudoku.engine.hints

import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry

/**
 * Naked and hidden subsets inside one unit. Both are the same search: pick k items whose
 * masks together cover exactly k bits. Naked items are cells covering digits; hidden items
 * are digits covering cell positions. Each call clears one subset's eliminations.
 */
internal object SubsetSteps {

    val NAKED_PAIR: EliminationStep = Subset(SudokuTechnique.NAKED_PAIR, size = 2, hidden = false)
    val HIDDEN_PAIR: EliminationStep = Subset(SudokuTechnique.HIDDEN_PAIR, size = 2, hidden = true)
    val NAKED_TRIPLE: EliminationStep = Subset(SudokuTechnique.NAKED_TRIPLE, size = 3, hidden = false)
    val HIDDEN_TRIPLE: EliminationStep = Subset(SudokuTechnique.HIDDEN_TRIPLE, size = 3, hidden = true)

    private class Subset(
        override val technique: SudokuTechnique,
        private val size: Int,
        private val hidden: Boolean,
    ) : EliminationStep {

        override fun eliminate(geometry: SudokuGeometry, candidates: IntArray): Boolean {
            val items = IntArray(geometry.size)
            for (unit in geometry.units) {
                val cells = geometry.cellsOf(unit)
                if (hidden) positionsByDigit(cells, candidates, items) else items.indices.forEach { items[it] = candidates[cells[it]] }
                if (search(cells, candidates, items, from = 0, picked = 0, chosen = 0, covered = 0)) return true
            }
            return false
        }

        /** Bit p of `items[d]` is set when the unit's p-th cell can hold digit d + 1. */
        private fun positionsByDigit(cells: IntArray, candidates: IntArray, items: IntArray) {
            items.fill(0)
            for (position in cells.indices) {
                var mask = candidates[cells[position]]
                while (mask != 0) {
                    val digit = Integer.numberOfTrailingZeros(mask)
                    items[digit] = items[digit] or (1 shl position)
                    mask = mask and (mask - 1)
                }
            }
        }

        /**
         * Empty items are skipped: an empty cell is placed and an empty digit is placed in the
         * unit, so neither may join a subset.
         */
        private fun search(
            cells: IntArray,
            candidates: IntArray,
            items: IntArray,
            from: Int,
            picked: Int,
            chosen: Int,
            covered: Int,
        ): Boolean {
            if (picked == size) return Integer.bitCount(covered) == size && clear(cells, candidates, chosen, covered)
            for (item in from until items.size) {
                val mask = items[item]
                if (mask == 0) continue
                val next = covered or mask
                if (Integer.bitCount(next) > size) continue
                if (search(cells, candidates, items, item + 1, picked + 1, chosen or (1 shl item), next)) return true
            }
            return false
        }

        private fun clear(cells: IntArray, candidates: IntArray, chosen: Int, covered: Int): Boolean {
            // Naked: the chosen cells use up the covered digits. Hidden: the chosen digits fill the covered cells.
            val positions = if (hidden) covered else chosen.inv()
            val strike = if (hidden) chosen.inv() else covered
            var cleared = false
            for (position in cells.indices) {
                if (positions and (1 shl position) == 0) continue
                val cell = cells[position]
                if (candidates[cell] and strike != 0) {
                    candidates[cell] = candidates[cell] and strike.inv()
                    cleared = true
                }
            }
            return cleared
        }
    }
}
