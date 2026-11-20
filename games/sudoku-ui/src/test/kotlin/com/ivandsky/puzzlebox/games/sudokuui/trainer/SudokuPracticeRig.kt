package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuPractice
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuPracticeJudgement
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTechniqueIds
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTrainerMove
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * A live practice board on a fixture puzzle, played through the real play state's gestures (select a square, then a
 * digit key, in notes mode for a note). The engine's own judge picks the moves a test plays; what the tests assert is
 * what the board did with them.
 */
internal class SudokuPracticeRig(val name: String, val puzzle: SudokuPuzzle, val technique: TechniqueId) {

    /** What the board reported to the trainer's count, in order. */
    val verdicts = ArrayList<TrainerVerdict>()

    val practice: SudokuPracticeBoard =
        SudokuTrainerFixtures.trainer.practice(SudokuTrainerFixtures.canonical(puzzle), technique, CoroutineScope(Job())) { verdicts += it }
            as SudokuPracticeBoard

    val label: String get() = "$name ${technique.value}"
    val state get() = practice.state
    val board: SudokuBoard get() = state.session.board
    val status: PracticeStatus get() = practice.status.value

    /** Every move open on the board now: each digit on each open square, and each shown note taken off. */
    fun moves(): List<SudokuTrainerMove> = board.cells.indices.filter { board.digitAt(it) == 0 }.flatMap { idx ->
        val notes = (board.cells[idx] as? SudokuCell.Notes)?.mask ?: 0
        (1..puzzle.geometry.size).map<Int, SudokuTrainerMove> { SudokuTrainerMove.Place(idx, it) } +
            (1..puzzle.geometry.size).filter { notes and (1 shl (it - 1)) != 0 }.map { SudokuTrainerMove.RemoveNote(idx, it) }
    }

    fun judge(move: SudokuTrainerMove): SudokuPracticeJudgement = SudokuPractice.judge(puzzle, board, move)

    /** The first open move of the kind [kind] that the judge reads as [T], null when the board has none. */
    inline fun <reified T : SudokuPracticeJudgement, reified M : SudokuTrainerMove> find(): M? =
        moves().filterIsInstance<M>().firstOrNull { judge(it) is T }

    /** A digit that does not belong on the first open square, as a move. */
    fun wrongDigit(): SudokuTrainerMove.Place {
        val idx = board.cells.indices.first { board.digitAt(it) == 0 }
        return SudokuTrainerMove.Place(idx, puzzle.solution[idx] % puzzle.geometry.size + 1)
    }

    /** The note of the first open square's solution digit, as a move to take it off. */
    fun solutionNote(): SudokuTrainerMove.RemoveNote {
        val idx = board.cells.indices.first { board.digitAt(it) == 0 }
        return SudokuTrainerMove.RemoveNote(idx, puzzle.solution[idx])
    }

    /** Whether the note for [digit] shows on the square [idx]. */
    fun noteOn(idx: Int, digit: Int): Boolean = ((board.cells[idx] as? SudokuCell.Notes)?.mask ?: 0) and (1 shl (digit - 1)) != 0

    /**
     * Plays [move] with the real gestures: the Notes toggle to match, the square, then the key. Returns the session as it
     * stood just before the key went in, which a move that is undone must leave exactly as it was.
     */
    fun play(move: SudokuTrainerMove): SudokuSession {
        val (idx, digit, notes) = when (move) {
            is SudokuTrainerMove.Place -> Triple(move.idx, move.digit, false)
            is SudokuTrainerMove.RemoveNote -> Triple(move.idx, move.digit, true)
            else -> error("no gesture makes $move")
        }
        if (state.session.notesMode != notes) state.toggleNotesMode()
        state.select(idx)
        val before = state.session
        state.input(digit)
        return before
    }

    companion object {

        /** A fresh rig for every fixture and every technique it can start a practice for. */
        fun all(): List<SudokuPracticeRig> = SudokuTrainerFixtures.all.flatMap { (name, puzzle) ->
            SudokuTechniqueIds.all.filter { SudokuPractice.start(puzzle, it) != null }.map { SudokuPracticeRig(name, puzzle, it) }
        }
    }
}
