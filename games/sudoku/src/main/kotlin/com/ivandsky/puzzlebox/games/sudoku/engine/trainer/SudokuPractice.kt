package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuCommand
import com.ivandsky.puzzlebox.games.sudoku.SudokuMoves
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep

/**
 * The Mini Sudoku practice board's rules (spec §2.2): where the board starts, how a move is judged against the easiest
 * rung of the hint ladder ([SudokuLadder]), and what Show me does. Pure: the play board owns the session.
 *
 * A practice board shows every open cell's candidates as notes, so the note-removing techniques can be played. The
 * notes it shows are the truth the ladder reads ([SudokuLadder.candidates]).
 */
object SudokuPractice {

    /**
     * The board a practice for [technique] starts on: the puzzle walked from its givens, taking at every point a
     * deduction of the easiest rung, until [technique] is that rung. Givens stay givens; what the walk placed is an
     * entry, and every open cell carries the candidates the walk left it as notes. A rung with several deductions is
     * walked by its cheapest.
     *
     * Null when [technique] is no Sudoku technique, when the puzzle's stored solution is not its unique solution
     * (the judge reads it), and when the walk is solved or stuck before [technique] is ever the easiest rung.
     */
    fun start(puzzle: SudokuPuzzle, technique: TechniqueId): SudokuBoard? {
        if (technique !in SudokuTechniqueIds.all || !SudokuTechniqueWalker.hasUniqueSolution(puzzle)) return null
        val geometry = puzzle.geometry
        var board = SudokuBoard.of(puzzle)
        val candidates = SudokuLadder.candidates(puzzle, board)
        board.cells.indices.filter { board.cells[it] == SudokuCell.Empty }.forEach { board = board.with(it, SudokuCell.Notes(candidates[it])) }
        // Each turn places a digit or takes a note off, and a board holds only cells * (digits + 1) of those.
        repeat(geometry.cellCount * (geometry.size + 1)) {
            val rung = SudokuLadder.easiest(puzzle, board) ?: return null
            if (rung.id == technique) return board
            board = commands(board, rung.steps.first()).fold(board) { acc, command -> command.apply(acc) }
        }
        error("the walk to $technique did not finish")
    }

    /**
     * [move] on [before], judged. A placement fits when it is the cell's solution digit and a note removal when it is
     * not; a move that does not fit is Wrong. A fitting move that some deduction of the easiest rung makes is Right,
     * whichever instance the player found; one the easiest rung does not make is Easier. With no rung to judge by,
     * a placement that fits is Right (a reveal's own step) and anything else is Wrong.
     */
    fun judge(puzzle: SudokuPuzzle, before: SudokuBoard, move: SudokuTrainerMove): SudokuPracticeJudgement {
        val rung = SudokuLadder.easiest(puzzle, before)
        val id = rung?.id
        val wrong = { reason: SudokuPracticeJudgement.Reason -> SudokuPracticeJudgement.Wrong(id, reason) }
        return when (move) {
            is SudokuTrainerMove.Place -> when {
                move.digit != puzzle.solution[move.idx] -> wrong(SudokuPracticeJudgement.Reason.WrongDigit)
                rung == null -> SudokuPracticeJudgement.Right(null, SudokuStep.Reveal(move.idx, move.digit))
                else -> verdict(rung, rung.steps.firstOrNull { it.places(move) })
            }
            is SudokuTrainerMove.RemoveNote -> when {
                move.digit == puzzle.solution[move.idx] -> wrong(SudokuPracticeJudgement.Reason.SolutionNote)
                rung == null -> wrong(SudokuPracticeJudgement.Reason.NotAMove)
                else -> verdict(rung, rung.steps.firstOrNull { it.strikes(move) })
            }
            is SudokuTrainerMove.AddNote, SudokuTrainerMove.Other -> wrong(SudokuPracticeJudgement.Reason.NotAMove)
        }
    }

    /**
     * The deduction Show me applies on [board]: the first of the easiest rung (a [SudokuPracticeJudgement.Right] by
     * shape: the step and its rung). With no rung to show, a reveal of the cell with the fewest candidates, the lowest
     * index on a tie. Null on a full board.
     */
    fun showMe(puzzle: SudokuPuzzle, board: SudokuBoard): SudokuPracticeJudgement.Right? {
        SudokuLadder.easiest(puzzle, board)?.let { return SudokuPracticeJudgement.Right(it.id, it.steps.first()) }
        val candidates = SudokuLadder.candidates(puzzle, board)
        val cell = board.cells.indices.filter { board.digitAt(it) == 0 }
            .minByOrNull { Integer.bitCount(candidates[it]) } ?: return null
        return SudokuPracticeJudgement.Right(null, SudokuStep.Reveal(cell, puzzle.solution[cell]))
    }

    /** The commands that make [step] on [board], in order: a placement, or the strike of each struck note. */
    fun commands(board: SudokuBoard, step: SudokuStep): List<SudokuCommand> = when (step) {
        is SudokuStep.Single -> listOfNotNull(SudokuMoves.place(board, step.place.cell, step.place.digit))
        is SudokuStep.Reveal -> listOfNotNull(SudokuMoves.place(board, step.cell, step.digit))
        is SudokuStep.Elimination -> step.struck.flatMap { (cell, mask) ->
            (1..board.geometry.size).filter { mask and (1 shl (it - 1)) != 0 }.mapNotNull { SudokuMoves.toggleNote(board, cell, it) }
        }
        is SudokuStep.Mistake, SudokuStep.Solved -> emptyList()
    }

    private fun verdict(rung: SudokuLadder.Rung, proof: SudokuStep?): SudokuPracticeJudgement =
        if (proof != null) SudokuPracticeJudgement.Right(rung.id, proof) else SudokuPracticeJudgement.Easier(rung.id, rung.steps.first())

    private fun SudokuStep.places(move: SudokuTrainerMove.Place): Boolean =
        this is SudokuStep.Single && place.cell == move.idx && place.digit == move.digit

    private fun SudokuStep.strikes(move: SudokuTrainerMove.RemoveNote): Boolean =
        this is SudokuStep.Elimination && (struck[move.idx] ?: 0) and (1 shl (move.digit - 1)) != 0
}
