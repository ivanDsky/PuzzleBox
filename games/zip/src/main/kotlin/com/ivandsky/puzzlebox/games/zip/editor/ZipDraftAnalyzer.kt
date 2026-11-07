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

/**
 * A Zip draft → its [Verdict]. A started but partial path is Incomplete; a path-less board
 * needs two waypoints (a shared board arrives that way) and is then solved from them alone.
 * Otherwise the bounded search counts up to two paths, so Unique, Multiple and None are exact.
 * Zip shows no difficulty and has no guessing gate. A spent or cancelled budget reads as
 * [Verdict.Undetermined].
 */
object ZipDraftAnalyzer {
    const val UNCOVERED: String = "zip.uncovered"   // IncompleteReason.count = cells off a non-empty path
    const val WAYPOINTS: String = "zip.waypoints"   // IncompleteReason.count = waypoints on a path-less board (< 2)

    /** Every IncompleteReason id [analyze] can emit, so a copy table can prove it covers them all. */
    val reasonIds: Set<String> = setOf(UNCOVERED, WAYPOINTS)

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
        val outcome = ZipSearch.bounded(puzzle, limit = 2, budget = budget)
        if (outcome !is ZipSearchOutcome.Done) return Verdict.Undetermined
        val found = outcome.solutions.map { path -> path.map(Cell::idx) }
        return when (found.size) {
            0 -> Verdict.None(emptySet())
            1 -> Verdict.Unique(Solution(found[0]))
            else -> Verdict.Multiple(Solution(found[0]), Solution(found[1]), differing(found[0], found[1]))
        }
    }

    /** Cells whose position in the two paths differs. */
    private fun differing(a: List<Int>, b: List<Int>): Set<Int> =
        a.indices.filter { a[it] != b[it] }.flatMapTo(HashSet()) { listOf(a[it], b[it]) }

    private const val MIN_WAYPOINTS = 2
}
