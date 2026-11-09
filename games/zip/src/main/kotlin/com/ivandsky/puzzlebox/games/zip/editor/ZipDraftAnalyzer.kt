package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import com.ivandsky.puzzlebox.games.zip.engine.ZipSearch
import com.ivandsky.puzzlebox.games.zip.engine.ZipSearchOutcome
import com.ivandsky.puzzlebox.games.zip.engine.ZipVerifier

/**
 * A Zip draft → its [Verdict]. A started but partial path is Incomplete; a path-less board
 * needs two waypoints (a shared board arrives that way) and is then solved from them alone.
 * Otherwise the bounded search counts up to two paths, so Unique, Multiple and None are exact.
 * A None verdict names the cells [ZipClashLocator] blames. Zip shows no difficulty and has no
 * guessing gate.
 *
 * The search stops after [MAX_NODES] DFS nodes, not after a time, so a draft gets the same
 * verdict on any device and under any load: past the cap, or once the caller gives up
 * ([Budget.cancelled]), it reads as [Verdict.Undetermined]. The clock in the budget is never
 * read.
 *
 * Multiple, as the 04 handoff has it: on a drawn board, A is the author's path and B the first
 * other path found; a path-less board has no reference, so A and B are the first two found.
 * The differing cells are the cells whose next cell differs between A and B.
 */
object ZipDraftAnalyzer {
    const val UNCOVERED: String = "zip.uncovered"   // IncompleteReason.count = cells off a non-empty path
    const val WAYPOINTS: String = "zip.waypoints"   // IncompleteReason.count = waypoints on a path-less board (< 2)

    /** Every IncompleteReason id [analyze] can emit, so a copy table can prove it covers them all. */
    val reasonIds: Set<String> = setOf(UNCOVERED, WAYPOINTS)

    /**
     * DFS nodes one analysis may visit: the 04 prototype's 300k-node budget. The 7×7 board with
     * a number on every turn of the row snake, the slowest board seen on device, settles in
     * about half of it.
     */
    const val MAX_NODES: Int = 300_000

    fun analyze(draft: ZipDraft, budget: Budget): Verdict {
        val cells = draft.n * draft.n
        if (draft.path.isNotEmpty() && draft.path.size < cells) {
            return Verdict.Incomplete(IncompleteReason(UNCOVERED, cells - draft.path.size))
        }
        if (draft.path.isEmpty() && draft.waypoints.size < MIN_WAYPOINTS) {
            return Verdict.Incomplete(IncompleteReason(WAYPOINTS, draft.waypoints.size))
        }
        val board = ZipBoard(draft.n, draft.walls, draft.waypoints.map(::Cell))
        val puzzle = ZipPuzzle(ZipEditor.variantOf(draft), board, solution = null)
        val outcome = ZipSearch.bounded(puzzle, limit = 2, budget = { budget.cancelled() }, maxNodes = MAX_NODES)
        if (outcome !is ZipSearchOutcome.Done) return Verdict.Undetermined
        val found = outcome.solutions.map { path -> path.map(Cell::idx) }
        return when (found.size) {
            0 -> Verdict.None(ZipClashLocator.conflicts(board))
            1 -> Verdict.Unique(Solution(found[0]))
            else -> multiple(puzzle, draft.path, found, cells)
        }
    }

    /** A is the author's path when it solves [puzzle] (a drawn board always does), else the first found. */
    private fun multiple(puzzle: ZipPuzzle, authored: List<Int>, found: List<List<Int>>, cells: Int): Verdict.Multiple {
        val a = authored.takeIf { solves(puzzle, it) } ?: found[0]
        val b = found.first { it != a }
        return Verdict.Multiple(Solution(a), Solution(b), differing(a, b, cells))
    }

    private fun solves(puzzle: ZipPuzzle, path: List<Int>): Boolean =
        path.size == puzzle.board.cellCount &&
            path.last() == puzzle.board.waypoints.last().idx &&
            ZipVerifier.verifyPath(puzzle, path.map(::Cell))

    /** Cells whose next cell on [a] is not their next cell on [b]. */
    private fun differing(a: List<Int>, b: List<Int>, cells: Int): Set<Int> {
        val nextOnB = IntArray(cells) { NO_CELL }
        b.zipWithNext { cell, next -> nextOnB[cell] = next }
        return a.zipWithNext().filter { (cell, next) -> nextOnB[cell] != next }.mapTo(HashSet()) { it.first }
    }

    private const val MIN_WAYPOINTS = 2
    private const val NO_CELL = -1
}
