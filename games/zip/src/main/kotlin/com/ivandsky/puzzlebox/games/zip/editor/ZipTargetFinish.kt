package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.ClosestCompletion
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import com.ivandsky.puzzlebox.games.zip.engine.ZipSearch
import com.ivandsky.puzzlebox.games.zip.engine.ZipSearchOutcome
import kotlin.random.Random

/**
 * Finish for me aimed at [target] (ticket 94). Each try finishes [draft] as with no target
 * ([ZipPathExtension], then [ZipPinnedRepair]) and grades the board as [ZipScoredGrader] does.
 * A board under the target is hardened: the numbers the repair added come off again one at a
 * time, then its walls, while the path stays the one answer. A board over it is softened: a wall
 * goes on the wrong move that costs the most, or a number splits the longest stretch, mixed as
 * the repair mixes them. The author's own numbers and walls, and the two end numbers, are never
 * touched.
 *
 * The first board at the target is the result. When no try gets there (a 5×5 board has too few
 * wrong moves for Expert) it is the closest board found: a miss is never a give-up.
 */
internal class ZipTargetFinish(
    private val draft: ZipDraft,
    private val random: Random,
    private val budget: Budget,
    private val target: Difficulty.Level,
) {
    private val n = draft.n
    private val closest = ClosestCompletion<ZipDraft>(target)

    /** The walls this try wants for each number when it softens, as [ZipPinnedRepair] draws them. */
    private var wallsPerNumber = 0.0

    fun completion(): Completion<ZipDraft> {
        repeat(TRIES) {
            val extension = ZipPathExtension(draft, budget)
            val path = extension.extend(random)
            val board = path?.let { ZipPinnedRepair(draft, it, random, budget).unique() }
            val hit = board != null && aim(board)
            if (hit || extension.noPath || budget.exhausted()) return closest.completion()
        }
        return closest.completion()
    }

    /** Offers [board], then the board hardened or softened toward the target; true on a hit. */
    private fun aim(board: ZipDraft): Boolean {
        val graded = graded(board, ZipDraftAnalyzer.MAX_NODES)
        if (closest.offer(board, graded?.level)) return true
        if (graded == null) return false
        wallsPerNumber = ZipPinnedRepair.DAILY_WALLS_PER_NUMBER.let { random.nextDouble(it.start, it.endInclusive) }
        val aimed = hardened(softened(graded))
        return aimed !== graded && closest.offer(aimed.board, aimed.level)
    }

    /** [from] with pieces added until it is no longer over the target; [from] itself when it is not. */
    private fun softened(from: Graded): Graded {
        var now = from
        while (now.level > target && !budget.exhausted()) {
            val next = (if (wantsNumber(now.board)) splitAStretch(now.board) else null)
                ?: now.worstMove?.let { now.board.copy(walls = now.board.walls + it) }
                ?: return now
            now = graded(next, ZipDraftAnalyzer.MAX_NODES) ?: return now
        }
        return now
    }

    /**
     * [from] with added pieces taken off until it is no longer under the target; [from] itself
     * when it is not. A piece stays when the board without it has another answer, takes too much
     * search to tell, or would be over the target.
     */
    private fun hardened(from: Graded): Graded {
        var now = from
        if (now.level >= target) return now
        for (piece in spare(now.board)) {
            if (budget.exhausted()) break
            val board = now.board
            val without = if (piece is Wall) board.copy(walls = board.walls - piece) else board.copy(waypoints = board.waypoints - (piece as Int))
            val next = graded(without, HARDEN_NODES) ?: continue
            if (next.level > target || !settles(without)) continue
            now = next
            if (now.level == target) break
        }
        return now
    }

    /**
     * The numbers (cells) and [Wall]s of [board] that are not the author's, and not an end number,
     * in the order to take them off: the numbers first, each kind shuffled. The repair's boards
     * carry more numbers than a daily one (ticket 97), so a harder board gives up numbers before
     * walls.
     */
    private fun spare(board: ZipDraft): List<Any> {
        val numbers = board.waypoints.drop(1).dropLast(1).filter { it !in draft.waypoints }
        // Sorted first: a wall's hash, and so the set's order, changes from run to run.
        val walls = board.walls.filter { it !in draft.walls }.sortedBy { it.cell.idx * 2 + it.direction.ordinal }
        return numbers.shuffled(random) + walls.shuffled(random)
    }

    private fun wantsNumber(board: ZipDraft): Boolean =
        board.walls.size >= wallsPerNumber * board.waypoints.size && board.waypoints.size < ZipPinnedRepair.NUMBERS_PER_SIDE * n

    /** [board] with a number on a cell inside its longest stretch without one; null when every cell of it has one. */
    private fun splitAStretch(board: ZipDraft): ZipDraft? {
        val numbered = board.waypoints.toSet()
        val places = board.path.indices.filter { board.path[it] in numbered }
        val (from, to) = places.zipWithNext().maxBy { (from, to) -> to - from }
        if (to - from <= 1) return null
        val cell = board.path[random.nextInt(from + 1, to)]
        return board.copy(waypoints = board.path.filter { it in numbered || it == cell })
    }

    /** [board] with its level, or null when the trace finds another answer or does not end within [nodeCap] and the budget. */
    private fun graded(board: ZipDraft, nodeCap: Int): Graded? {
        val trace = ZipSolveTrace(board, board.path, budget, nodeCap)
        if (trace.walk() != ZipSolveTrace.Outcome.Unique) return null
        return Graded(board, ZipScoredGrader.BANDS.levelOf(ZipScoredGrader.score(n, trace.points)), trace.worstMove)
    }

    /** Whether the analyzer's own search calls [board] unique well inside its cap, so the Studio does on any device. */
    private fun settles(board: ZipDraft): Boolean {
        val puzzle = ZipPuzzle(ZipEditor.variantOf(board), ZipBoard(n, board.walls, board.waypoints.map(::Cell)), solution = null)
        return (ZipSearch.bounded(puzzle, limit = 2, budget, SETTLE_NODES) as? ZipSearchOutcome.Done)?.solutions?.size == 1
    }

    private class Graded(val board: ZipDraft, val level: Difficulty.Level, val worstMove: Wall?)

    private companion object {
        /**
         * Tries before the closest board is the answer. A run with no clock ends here, so a seeded
         * run is the same on any machine. A 5×5 press for Expert, which it cannot reach, runs
         * them all in about half a second; a 7×7 or 8×8 run that misses is out of time first.
         */
        const val TRIES = 200

        /** Positions one trace may look at while a piece is taken off: past it the piece stays. */
        const val HARDEN_NODES = 2_000

        /**
         * DFS nodes the analyzer's search may take on a board with a piece off, a tenth of
         * [ZipDraftAnalyzer.MAX_NODES]: the trace prunes more than that search does, and a board
         * the search cannot settle is one the Studio could not check.
         */
        const val SETTLE_NODES = ZipDraftAnalyzer.MAX_NODES / 10
    }
}
