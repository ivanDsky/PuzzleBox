package com.ivandsky.puzzlebox.games.queensui.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.engine.trainer.QueensTrainerMove
import java.time.Instant

/**
 * The state of one snippet board: a [QueensSession] of the cut's board with Auto-cross on whatever the player's
 * setting is (A11), and the one move the player makes. The session is only ever drawn; the verdict is the move
 * check's, not the session's.
 *
 * A tap makes the move that follows the target step (a queen for a last-cell step, a cross for a cross step) on an
 * open square, once. A square holding a queen or a cross, a manual one or one a queen forces, takes no tap.
 */
internal class QueensSnippetBoardState(private val snippet: QueensSnippet) {

    /** The board as drawn: the cut's own, plus the player's move once made. */
    var session: QueensSession by mutableStateOf(cutSession(snippet))
        private set

    /** The player's move, null until one is made. */
    var played: QueensTrainerMove? = null
        private set

    /**
     * The player's move for a tap on the square [idx], now drawn; null when no move is made: one was made already,
     * [idx] is off the board, or the square holds a queen or a cross.
     */
    fun tap(idx: Int): QueensTrainerMove? {
        if (played != null || idx !in 0 until snippet.puzzle.cellCount) return null
        val cell = Cell(idx)
        if (cell in session.queens || session.markAt(cell) != null) return null
        val move = if (snippet.placesQueen) QueensTrainerMove.Place(cell) else QueensTrainerMove.Cross(cell)
        session = when (move) {
            is QueensTrainerMove.Place -> session.withQueenPlaced(cell, NOW)
            is QueensTrainerMove.Cross -> session.withCrossToggled(cell, NOW)
        }
        played = move
        return move
    }

    private companion object {
        /** A snippet has no clock; the session wants a time for each command. */
        val NOW: Instant = Instant.EPOCH

        /** The cut's crosses, then its queens: a queen never lands on a hand-drawn cross, so the crosses it forces fill in. */
        fun cutSession(snippet: QueensSnippet): QueensSession {
            val board = snippet.cut.boardBefore
            var session = QueensSession(ref = null, puzzle = snippet.puzzle)
            for (cell in board.manual) session = session.withCrossToggled(cell, NOW)
            for (cell in board.queens) session = session.withQueenPlaced(cell, NOW)
            return session
        }
    }
}
