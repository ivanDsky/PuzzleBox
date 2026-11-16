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
 * Walls and numbers go on together, in the mix of a daily board: each run draws its own
 * [wallsPerNumber] from [random], and the next piece is a wall while the board has fewer walls
 * than that, a number otherwise. So every board the repair had to add to has walls, and two
 * runs on one draft differ in how walled they are.
 *
 * The search is the analyzer's ([ZipSearch.bounded], two answers at most) under a tighter node
 * cap. It does not read the clock, so a board it settles is one [ZipDraftAnalyzer] calls Unique
 * on any device.
 */
internal class ZipPinnedRepair(
    private val draft: ZipDraft,
    private val path: List<Int>,
    private val random: Random,
    private val budget: Budget,
) {
    private val n = draft.n
    private val answer = path.map(::Cell)
    private val positionOf = IntArray(n * n).also { positions -> path.forEachIndexed { at, cell -> positions[cell] = at } }

    /** Path positions that carry a number: both ends and the author's numbers. */
    private val numbered = sortedSetOf(0, path.lastIndex).apply { draft.waypoints.forEach { add(positionOf[it]) } }
    private val walls = draft.walls.toMutableSet()

    /** Numbers are added up to this many on the board; past it only walls are. */
    private val numbersWanted = NUMBERS_PER_SIDE * n

    /** The walls this run wants for each number on the board, somewhere in [DAILY_WALLS_PER_NUMBER]. */
    private val wallsPerNumber = random.nextDouble(DAILY_WALLS_PER_NUMBER.start, DAILY_WALLS_PER_NUMBER.endInclusive)

    /** The finished board, or null once [budget] is spent. */
    fun unique(): ZipDraft? {
        // A board the author drew in full may be finished already: the analyzer's own cap for its first search.
        var nodes = if (draft.path.size == path.size) ZipDraftAnalyzer.MAX_NODES else SEARCH_NODES
        // Every round but the last adds a number or a wall, and a board holds under 2n² of those.
        repeat(2 * n * n) {
            val numbers = numbered.map { path[it] }
            val puzzle = ZipPuzzle(ZipEditor.variantOf(draft), ZipBoard(n, walls, numbers.map(::Cell)), solution = null)
            val found = (ZipSearch.bounded(puzzle, limit = 2, budget, nodes) as? ZipSearchOutcome.Done)?.solutions
            nodes = SEARCH_NODES
            when {
                found == null -> if (budget.exhausted()) return null else tighten()
                found.size == 1 -> return ZipDraft(n, walls.toSet(), path, numbers)
                else -> ruleOut(found.firstOrNull { it != answer } ?: return null)
            }
        }
        return null
    }

    /**
     * Makes [other] no answer, or less likely one. When the mix asks for a number: one on a
     * cell [other] reaches between other numbers than [path] does, or with no such cell (a
     * board with its two end numbers has none) one that splits a stretch, for the next round to
     * tell the two paths by. Otherwise a wall on a step [other] takes and [path] does not; two
     * different full paths from one start always differ in such a step.
     */
    private fun ruleOut(other: List<Cell>) {
        if (wantsNumber()) {
            var passed = 0
            val misplaced = other.map { positionOf[it.idx] }.filter { at ->
                if (at in numbered) passed++
                at !in numbered && numbered.headSet(at).size != passed
            }
            if (misplaced.isNotEmpty()) {
                numbered += misplaced.random(random)
                return
            }
            if (splitAStretch()) return
        }
        val (from, to) = other.zipWithNext().filter { (a, b) -> !joined(a.idx, b.idx) }.random(random)
        walls += wallBetween(from.idx, to.idx)
    }

    /**
     * The search did not settle under its cap: the board is too open to count answers on. When
     * the mix asks for a number, one that splits a stretch; otherwise, and when no stretch has
     * a cell to number, a wall on a step [path] does not take.
     */
    private fun tighten() {
        if (wantsNumber() && splitAStretch()) return
        // By cell index, not from a set of walls: a wall's hash, and so a set's order, changes from run to run.
        val spare = (0 until n * n).flatMap { cell ->
            listOfNotNull((cell + 1).takeIf { cell % n < n - 1 }, (cell + n).takeIf { cell / n < n - 1 })
                .filter { !joined(cell, it) }
                .map { wallBetween(cell, it) }
        }.filter { it !in walls }
        spare.randomOrNull(random)?.let { walls += it }
    }

    /** Numbers a cell inside the longest stretch of [path] without a number; false when every cell of it has one. */
    private fun splitAStretch(): Boolean {
        val (from, to) = numbered.zipWithNext().maxBy { (from, to) -> to - from }
        if (to - from <= 1) return false
        numbered += random.nextInt(from + 1, to)
        return true
    }

    /** Whether the next piece is a number: the board has its walls for the numbers on it, and room for one more. */
    private fun wantsNumber(): Boolean = walls.size >= wallsPerNumber * numbered.size && numbered.size < numbersWanted

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

        /**
         * Walls per number on the captured LinkedIn dailies that have walls: 4 to 6 on #592
         * (6×6) at the least, 16 to 8 on an 8×8 at the most. Too few boards to tell the sizes
         * apart, so every size draws from the one range.
         */
        val DAILY_WALLS_PER_NUMBER = 2.0 / 3..2.0
    }
}
