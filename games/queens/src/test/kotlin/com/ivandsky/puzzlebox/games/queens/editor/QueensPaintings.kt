package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensGenerator
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSearch
import kotlin.random.Random

/**
 * Fully painted boards an author could have painted, every region in one piece: the input the
 * repaint's tests and measurement cut their drafts from. The intended answer rides along as
 * the puzzle's solution.
 */
object QueensPaintings {
    /**
     * A [size] board with one answer. A generated board may have split regions, so this is the
     * completion of one cut back to the minimum of started regions.
     */
    fun unique(size: Int, random: Random): QueensPuzzle {
        val generator = QueensGenerator(random)
        while (true) {
            val board = checkNotNull(generator.nextUnique(size)) { "no board for size $size" }
            val draft = QueensDraftCuts.cut(board, QueensCompleter.minimumStarted(size), crowns = 0, random)
            val done = QueensCompleter.complete(draft, random, Budget.Unlimited) as? Completion.Done ?: continue
            val painting = QueensPuzzle(size, done.draft.regions)
            return painting.copy(solution = QueensSearch.solutions(painting, limit = 1).single())
        }
    }

    /**
     * A [size] board painted by hand: regions of uneven size spread around an intended answer
     * and never repaired, so it has two answers or more.
     */
    fun ambiguous(size: Int, random: Random): QueensPuzzle {
        val generator = QueensGenerator(random)
        while (true) {
            val queens = checkNotNull(generator.nextUnique(size)?.solution) { "no placement for size $size" }.map { it.idx }
            val regions = spread(size, queens.shuffled(random), random)
            val painting = QueensPuzzle(size, regions)
            if (QueensSearch.solutions(painting, limit = 2).size == 2) return painting.copy(solution = queens.map(::Cell))
        }
    }

    /** Region i starts on the i-th of [seeds]; then a random unpainted cell beside paint joins a random neighbour's region. */
    private fun spread(n: Int, seeds: List<Int>, random: Random): List<Int> {
        val regions = MutableList(n * n) { QueensDraft.UNPAINTED }
        seeds.forEachIndexed { region, cell -> regions[cell] = region }
        while (true) {
            val open = regions.indices.filter { cell -> regions[cell] == QueensDraft.UNPAINTED && painted(n, regions, cell).isNotEmpty() }
            if (open.isEmpty()) return regions
            val cell = open[random.nextInt(open.size)]
            regions[cell] = painted(n, regions, cell).let { it[random.nextInt(it.size)] }
        }
    }

    /** The regions painted on the 4-neighbours of [cell]. */
    private fun painted(n: Int, regions: List<Int>, cell: Int): List<Int> = listOfNotNull(
        (cell - n).takeIf { cell / n > 0 },
        (cell + n).takeIf { cell / n < n - 1 },
        (cell - 1).takeIf { cell % n > 0 },
        (cell + 1).takeIf { cell % n < n - 1 },
    ).map { regions[it] }.filter { it != QueensDraft.UNPAINTED }
}
