package com.ivandsky.puzzlebox.games.sudoku

import com.ivandsky.puzzlebox.game.replay.GameReplay
import com.ivandsky.puzzlebox.game.replay.ReplayFacts
import com.ivandsky.puzzlebox.game.replay.ReplayMove
import java.time.Instant

/**
 * Sudoku solve replay, shared by Mini and Classic: a recorded move runs through the session
 * reducer live play calls for it, so a replayed board cannot drift from the played one.
 */
object SudokuReplay : GameReplay<SudokuSession> {

    override fun step(session: SudokuSession, move: ReplayMove, atMs: Long): SudokuSession? {
        val now = Instant.ofEpochMilli(atMs)
        val board = session.board
        return when (val typed = SudokuReplayMove.of(move, board.geometry)) {
            null -> null
            is SudokuReplayMove.Place -> session.command(SudokuMoves.place(board, typed.idx, typed.digit), now)
            is SudokuReplayMove.ToggleNote -> session.command(SudokuMoves.toggleNote(board, typed.idx, typed.digit), now)
            is SudokuReplayMove.Erase -> session.command(SudokuMoves.erase(board, typed.idx), now)
            SudokuReplayMove.Clear -> session.command(SudokuMoves.clear(board), now)
            SudokuReplayMove.Undo -> session.withUndo()
            SudokuReplayMove.Redo -> session.withRedo()
            is SudokuReplayMove.HintReveal ->
                // A hint only ever reveals the solution's digit; anything else is not a record of one.
                if (typed.digit != session.puzzle.solution[typed.idx]) null
                else session.command(SudokuMoves.reveal(board, typed.idx, typed.digit), now)
            SudokuReplayMove.Hint -> session.withHintUsed()
        }
    }

    override fun factsOf(initial: SudokuSession): (SudokuSession) -> ReplayFacts {
        val puzzle = initial.puzzle
        val total = puzzle.givens.count { it == 0 }
        return { session ->
            ReplayFacts(
                hints = session.hintsUsed,
                correct = puzzle.givens.indices.count { idx ->
                    puzzle.givens[idx] == 0 && session.board.digitAt(idx) == puzzle.solution[idx]
                },
                total = total,
                solved = session.isComplete,
            )
        }
    }

    /** A builder returns null for a move that would change nothing. */
    private fun SudokuSession.command(command: SudokuCommand?, now: Instant): SudokuSession =
        if (command == null) this else withCommand(command, now)
}
