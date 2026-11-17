package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.linkedin.LinkedInOpening
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlay
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlayValue
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSolve
import com.ivandsky.puzzlebox.game.replay.MoveKind

/**
 * Encodes a solved Queens [LinkedInSolve] as the LinkedIn play record (the final board, the colours of the first
 * queens placed on a solution cell) and the opening play the web client saves before any queen: an empty board.
 * The wire shape is research 03, Addendum 7, read from the captured Queens win and first-move requests.
 */
object QueensLinkedInPlay {

    private const val BOARD_KEY = "queensBoardStateBinding"
    private const val SOLVE_ORDER_KEY = "queensSolveOrderBinding"
    private const val QUEEN = "QUEEN"
    private const val CROSS = "CROSS"
    private const val EMPTY = "EMPTY"
    private const val SOLVE_ORDER_LENGTH = 3
    private const val OPENING_MAX_MS = 1_000L

    /** The web client's colour name for a region id (the puzzle's `colorGrid` value), indexed by id. */
    private val REGION_COLOURS = listOf(
        "purple", "orange", "blue", "green", "gray", "red", "yellow", "brown",
        "red", "blue", "blue", "red", "green", "blue", "purple", "yellow",
    )

    private val FLAWS = setOf(MoveKind.Mistake, MoveKind.Hint, MoveKind.Undo)

    /** Null when the final board is not solved: LinkedIn only takes a finished board. */
    fun encode(solve: LinkedInSolve<QueensSession>): LinkedInPlay? {
        val final = solve.final
        if (!final.isComplete) return null
        val cells = final.puzzle.cellCount
        return LinkedInPlay(
            states = mapOf(
                BOARD_KEY to LinkedInPlayValue.Strings(boardOf(final)),
                SOLVE_ORDER_KEY to LinkedInPlayValue.Strings(solveOrderOf(solve)),
            ),
            payloadKeys = mapOf("playRecord" to BOARD_KEY, "solveOrder" to SOLVE_ORDER_KEY),
            mistakeFree = solve.kinds.none { it in FLAWS },
            opening = LinkedInOpening(
                states = mapOf(
                    BOARD_KEY to LinkedInPlayValue.Strings(List(cells) { EMPTY }),
                    SOLVE_ORDER_KEY to LinkedInPlayValue.Strings(emptyList()),
                ),
                timeMs = openingTimeMs(solve.timesMs.firstOrNull()),
            ),
        )
    }

    /** Row-major: a queen is QUEEN, any other marked cell CROSS, the rest EMPTY. */
    private fun boardOf(session: QueensSession): List<String> {
        val queens = session.queens.toSet()
        return List(session.puzzle.cellCount) { idx ->
            val cell = Cell(idx)
            when {
                cell in queens -> QUEEN
                cell in session.marks -> CROSS
                else -> EMPTY
            }
        }
    }

    /**
     * The colour names of the first [SOLVE_ORDER_LENGTH] queens placed on a cell of the final (solved) board, in
     * placement order. A queen counts at each placement, as the client counts it; the list only grows to its cap.
     */
    private fun solveOrderOf(solve: LinkedInSolve<QueensSession>): List<String> {
        val puzzle = solve.final.puzzle
        val solution = solve.final.queens.toSet()
        val order = ArrayList<String>(SOLVE_ORDER_LENGTH)
        var standing: Set<Cell> = emptySet()
        for (board in solve.boards) {
            for (queen in board.queens) {
                if (order.size == SOLVE_ORDER_LENGTH) return order
                if (queen !in standing && queen in solution) order += REGION_COLOURS[puzzle.regionOf(queen)]
            }
            standing = board.queens.toSet()
        }
        return order
    }

    /** The first move's time, capped at one second and never under 1 ms, as the client's first save reads. */
    private fun openingTimeMs(firstMoveMs: Long?): Long =
        minOf(OPENING_MAX_MS, firstMoveMs ?: OPENING_MAX_MS).coerceAtLeast(1L)
}
