package com.ivandsky.puzzlebox.games.sudoku

import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlay
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSolve
import com.ivandsky.puzzlebox.game.linkedin.LinkedInVoyagerCell
import com.ivandsky.puzzlebox.game.linkedin.LinkedInVoyagerPlay

/**
 * Encodes a solved Mini Sudoku [LinkedInSolve] for LinkedIn's voyager save, the wire Mini Sudoku is still on
 * (ADR-0032 §11): the digits the player entered on the solved board, and the board of the first save the web
 * client makes, which is the first entered digit alone. A preset is never sent, and neither is a note.
 */
object MiniSudokuLinkedInPlay {

    private const val STATE_NAME = "miniSudokuGameState"
    private const val MIN_OPENING_MS = 1L

    /** Null when the final board is not solved, or holds no digit of the player's: LinkedIn only takes a finished board that was played. */
    fun encode(solve: LinkedInSolve<SudokuSession>): LinkedInPlay? {
        val final = solve.final
        if (!final.isComplete) return null
        // Notes change the board without entering a digit, so the first tick is not always the first save.
        val first = solve.boards.indexOfFirst { entered(it.board).isNotEmpty() }
        if (first < 0) return null
        return LinkedInPlay(
            states = emptyMap(),
            payloadKeys = emptyMap(),
            // Same rule as Zip and Tango: a conflicting digit, a hint or an undo anywhere in the solve is a flaw.
            mistakeFree = solve.mistakeFree,
            voyager = LinkedInVoyagerPlay(
                stateName = STATE_NAME,
                cells = entered(final.board),
                openingCells = entered(solve.boards[first].board),
                openingTimeMs = solve.timesMs[first].coerceAtLeast(MIN_OPENING_MS),
            ),
        )
    }

    /** The digits the player entered, ascending by cell index. */
    private fun entered(board: SudokuBoard): List<LinkedInVoyagerCell> =
        board.cells.mapIndexedNotNull { idx, cell -> (cell as? SudokuCell.Entry)?.let { LinkedInVoyagerCell(idx, it.digit) } }
}
