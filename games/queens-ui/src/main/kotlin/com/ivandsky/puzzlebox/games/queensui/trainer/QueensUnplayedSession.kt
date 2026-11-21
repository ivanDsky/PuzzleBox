package com.ivandsky.puzzlebox.games.queensui.trainer

import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensSession
import java.time.Instant

/**
 * [board] as a session nobody has played, Auto-cross on whatever the player's setting is (A11): the crosses, then the
 * queens, so the crosses a queen forces fill in. The setup is no move: nothing to undo. Both a practice board and an
 * intro demo's frames start from it.
 */
internal fun unplayedSession(puzzle: QueensPuzzle, board: QueensBoard): QueensSession {
    val fresh = QueensSession(ref = null, puzzle = puzzle.copy(solution = null, unknownTail = ByteArray(0)))
    val crossed = board.manual.fold(fresh) { session, cell -> session.withCrossToggled(cell, Instant.EPOCH) }
    return board.queens.fold(crossed) { session, cell -> session.withQueenPlaced(cell, Instant.EPOCH) }
        .copy(history = CommandHistory())
}
