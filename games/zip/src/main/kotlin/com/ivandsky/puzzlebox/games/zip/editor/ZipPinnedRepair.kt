package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.ZipGenerator
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import com.ivandsky.puzzlebox.games.zip.engine.ZipSearch
import com.ivandsky.puzzlebox.games.zip.engine.ZipSearchOutcome
import kotlin.math.abs
import kotlin.random.Random

/**
 * [path] drawn on [draft], then numbers and walls added until it is the board's one answer
 * (Finish for me). The author's numbers and walls all stay. As in [ZipGenerator], a number goes
 * on a cell of the path and a wall between two cells the path does not join, so [path] stays an
 * answer whatever is added. Here each one is placed to rule out another answer the search
 * found, which takes far fewer walls than the generator's random ones.
 *
 * The search is the analyzer's ([ZipSearch.bounded], two answers at most) under a tighter node
 * cap. It does not read the clock, so a board it settles is one [ZipDraftAnalyzer] calls Unique
 * on any device.
 */
internal class ZipPinnedRepair(private val draft: ZipDraft, private val path: List<Int>, private val budget: Budget) {
    private val n = draft.n
    private val answer = path.map(::Cell)
    private val positionOf = IntArray(n * n).also { positions -> path.forEachIndexed { at, cell -> positions[cell] = at } }

    /** Path positions that carry a number: both ends and the author's numbers. */
    private val numbered = sortedSetOf(0, path.lastIndex).apply { draft.waypoints.forEach { add(positionOf[it]) } }
    private val walls = draft.walls.toMutableSet()

    /** Numbers are added up to this many on the board; past it only walls are. */
    private val numbersWanted = NUMBERS_PER_SIDE * n

    /** The finished board, or null once [budget] is spent. */
    fun unique(random: Random): ZipDraft? {
        // A board the author drew in full may be finished already: the analyzer's own cap for its first search.
        var nodes = if (draft.path.size == path.size) ZipDraftAnalyzer.MAX_NODES else SEARCH_NODES
        // Every round but the last adds a number or a wall, and a board holds under 2n² of those.
        repeat(2 * n * n) {
            val numbers = numbered.map { path[it] }
            val puzzle = ZipPuzzle(ZipEditor.variantOf(draft), ZipBoard(n, walls, numbers.map(::Cell)), solution = null)
            val found = (ZipSearch.bounded(puzzle, limit = 2, budget, nodes) as? ZipSearchOutcome.Done)?.solutions
            nodes = SEARCH_NODES
            when {
                found == null -> if (budget.exhausted()) return null else tighten(random)
                found.size == 1 -> return ZipDraft(n, walls.toSet(), path, numbers)
                else -> ruleOut(found.firstOrNull { it != answer } ?: return null, random)
            }
        }
        return null
    }

    /**
     * Makes [other] no answer. While the board wants numbers: a number on a cell [other] reaches
     * between other numbers than [path] does. Otherwise a wall on a step [other] takes and
     * [path] does not; two different full paths from one start always differ in such a step.
     */
    private fun ruleOut(other: List<Cell>, random: Random) {
        if (numbered.size < numbersWanted) {
            var passed = 0
            val misplaced = other.map { positionOf[it.idx] }.filter { at ->
                if (at in numbered) passed++
                at !in numbered && numbered.headSet(at).size != passed
            }
            if (misplaced.isNotEmpty()) {
                numbered += misplaced.random(random)
                return
            }
        }
        val (from, to) = other.zipWithNext().filter { (a, b) -> !joined(a.idx, b.idx) }.random(random)
        walls += wallBetween(from.idx, to.idx)
    }

    /**
     * The search did not settle under its cap: the board is too open to count answers on.
     * Numbers a cell inside the longest stretch of [path] without one, or, when the board has
     * its numbers, walls off a step [path] does not take.
     */
    private fun tighten(random: Random) {
        val (from, to) = numbered.zipWithNext().maxBy { (from, to) -> to - from }
        if (numbered.size < numbersWanted && to - from > 1) {
            numbered += random.nextInt(from + 1, to)
            return
        }
        // By cell index, not from a set of walls: a wall's hash, and so a set's order, changes from run to run.
        val spare = (0 until n * n).flatMap { cell ->
            listOfNotNull((cell + 1).takeIf { cell % n < n - 1 }, (cell + n).takeIf { cell / n < n - 1 })
                .filter { !joined(cell, it) }
                .map { wallBetween(cell, it) }
        }.filter { it !in walls }
        spare.randomOrNull(random)?.let { walls += it }
    }

    /** Whether [path] steps between [a] and [b]. */
    private fun joined(a: Int, b: Int): Boolean = abs(positionOf[a] - positionOf[b]) == 1

    private fun wallBetween(a: Int, b: Int): Wall =
        Wall(Cell(minOf(a, b)), if (abs(a - b) == 1) WallDirection.RIGHT else WallDirection.DOWN)

    private companion object {
        /**
         * DFS nodes one search may visit, a thirtieth of [ZipDraftAnalyzer.MAX_NODES]. A search
         * that cannot settle costs the run this much before the board is tightened blind: a
         * higher cap gives a few walls fewer at 8×8 and takes several times as long.
         */
        const val SEARCH_NODES = 10_000

        /** Numbers wanted per board side before walls take over: at most 10 on 5×5, 16 on 8×8. */
        const val NUMBERS_PER_SIDE = 2
    }
}
