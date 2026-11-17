package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.game.linkedin.LinkedInOpening
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlay
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlayValue
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSolve
import com.ivandsky.puzzlebox.game.replay.MoveKind

/**
 * Encodes a solved Tango [LinkedInSolve] as the LinkedIn play record (the final board and the order the cells were
 * correctly placed in) and the opening play the web client sends before the win: the preset cells plus the first
 * placed cell. The wire names a sun `ZERO` and a moon `ONE`, as the feed parser reads them.
 */
object TangoLinkedInPlay {

    private const val BOARD_KEY = "tangoBoardStateBinding"
    private const val SOLVE_ORDER_KEY = "tangoSolveOrderBinding"
    private const val SUN = "ZERO"
    private const val MOON = "ONE"
    private const val EMPTY = "EMPTY"
    private const val PRESET_RANK = "-1"
    private const val MIN_OPENING_MS = 1L

    /** Null when the final board is not solved: LinkedIn only takes a finished board. */
    fun encode(solve: LinkedInSolve<TangoSession>): LinkedInPlay? {
        val final = solve.final
        if (!final.isComplete) return null
        val opening = opening(solve)
        return LinkedInPlay(
            states = mapOf(
                BOARD_KEY to LinkedInPlayValue.Strings(board(final.puzzle, final.grid())),
                SOLVE_ORDER_KEY to LinkedInPlayValue.Strings(solveOrder(solve)),
            ),
            payloadKeys = mapOf("playRecord" to BOARD_KEY, "solveOrder" to SOLVE_ORDER_KEY),
            // Same rule as Zip: a rejected drop, hint or undo anywhere in the solve is a flaw.
            mistakeFree = final.mistakeCount == 0 && solve.kinds.none { it in FLAWS },
            opening = opening,
        )
    }

    /** The preset cells plus the first cell the player placed, with its value at that tick; no solve order yet. */
    private fun opening(solve: LinkedInSolve<TangoSession>): LinkedInOpening {
        val puzzle = solve.final.puzzle
        val first = solve.boards.firstOrNull { it.placements.isNotEmpty() }
        val placed = first?.placements?.entries?.minByOrNull { it.key }
        val grid = List(puzzle.cellCount) { idx -> puzzle.givens[idx] ?: placed?.takeIf { it.key == idx }?.value }
        return LinkedInOpening(
            states = mapOf(BOARD_KEY to LinkedInPlayValue.Strings(board(puzzle, grid))),
            timeMs = (solve.timesMs.firstOrNull() ?: 0L).coerceAtLeast(MIN_OPENING_MS),
        )
    }

    private fun board(puzzle: TangoPuzzle, grid: List<TangoCell?>): List<String> =
        List(puzzle.cellCount) { idx ->
            when (grid[idx]) {
                TangoCell.SUN -> SUN
                TangoCell.MOON -> MOON
                null -> EMPTY
            }
        }

    /**
     * `"-1"` for a preset, else the cell's rank among the correct placements. A cell leaves the order when its value
     * changes away from the solved one and joins the end when it is placed correctly again.
     */
    private fun solveOrder(solve: LinkedInSolve<TangoSession>): List<String> {
        val puzzle = solve.final.puzzle
        val solved = solve.final.grid()
        val order = LinkedHashSet<Int>()
        var previous: TangoPlacements = emptyMap()
        for (board in solve.boards) {
            for (idx in 0 until puzzle.cellCount) {
                val value = board.placements[idx]
                if (value == previous[idx]) continue
                order.remove(idx)
                if (value != null && value == solved[idx]) order.add(idx)
            }
            previous = board.placements
        }
        val ranks = order.withIndex().associate { (rank, idx) -> idx to rank }
        return List(puzzle.cellCount) { idx -> if (idx in puzzle.givens) PRESET_RANK else ranks.getValue(idx).toString() }
    }

    private val FLAWS = setOf(MoveKind.Mistake, MoveKind.Hint, MoveKind.Undo)
}
