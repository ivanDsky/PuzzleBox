package com.ivandsky.puzzlebox.games.strands.engine

import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzle
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.games.strands.StrandsWord

/**
 * The first-move hint target (nav-rethink B4): the most constrained unfound theme word that the
 * board does not already give away. Strands has no forced-move proof, so the measure is, in order:
 *
 * 1. fewest routes: the ways to trace the word's spelling through the letters still free
 *    (letters of found words are spent);
 * 2. fewest ways out: the free letters outside the word that touch it, so a word hemmed in by
 *    found words and board edges comes first;
 * 3. the shorter word, then feed order.
 *
 * A word with no way out is already outlined by the found words and the edges, which is all the
 * first hint stage would show, so it is skipped while another theme word is left.
 */
internal object StrandsFirstMovePicker {

    /** Search steps one word may take; a word that needs more counts as having countless routes. */
    private const val STEP_BUDGET = 20_000

    /** The theme word to hint next, or null once every theme word is found. */
    fun pick(state: StrandsState): StrandsWord? {
        val puzzle = state.puzzle
        val unfound = puzzle.themeWords.filter { it.text !in state.found }
        if (unfound.size < 2) return unfound.firstOrNull()
        val free = BooleanArray(puzzle.rows * puzzle.cols) { true }
        (puzzle.themeWords + puzzle.spangram).forEach { word ->
            if (word.text in state.found) word.cells.forEach { free[puzzle.index(it)] = false }
        }
        val waysOut = unfound.associateWith { waysOut(puzzle, free, it) }
        return unfound.filter { waysOut.getValue(it) > 0 }.ifEmpty { unfound }
            .minWithOrNull(compareBy({ routes(puzzle, free, it.text) }, { waysOut.getValue(it) }, { it.text.length }))
    }

    private fun StrandsPuzzle.index(cell: Coord): Int = cell.row * cols + cell.col

    private fun waysOut(puzzle: StrandsPuzzle, free: BooleanArray, word: StrandsWord): Int {
        val own = word.cells.mapTo(HashSet()) { puzzle.index(it) }
        val touching = HashSet<Int>()
        word.cells.forEach { cell ->
            puzzle.forEachNeighbour(puzzle.index(cell)) { if (free[it] && it !in own) touching += it }
        }
        return touching.size
    }

    private fun routes(puzzle: StrandsPuzzle, free: BooleanArray, text: String): Int {
        val open = free.copyOf()
        var steps = 0
        var routes = 0

        fun extend(at: Int, next: Int) {
            if (++steps > STEP_BUDGET) return
            if (next == text.length) {
                routes++
                return
            }
            open[at] = false
            puzzle.forEachNeighbour(at) { if (open[it] && puzzle.letters[it] == text[next]) extend(it, next + 1) }
            open[at] = true
        }

        for (start in open.indices) if (open[start] && puzzle.letters[start] == text[0]) extend(start, 1)
        return if (steps > STEP_BUDGET) Int.MAX_VALUE else routes
    }

    private inline fun StrandsPuzzle.forEachNeighbour(index: Int, action: (Int) -> Unit) {
        val row = index / cols
        val col = index % cols
        for (r in row - 1..row + 1) for (c in col - 1..col + 1) {
            if (r in 0 until rows && c in 0 until cols && (r != row || c != col)) action(r * cols + c)
        }
    }
}
