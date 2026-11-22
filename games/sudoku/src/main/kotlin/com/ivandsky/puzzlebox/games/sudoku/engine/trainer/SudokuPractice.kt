package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuCommand
import com.ivandsky.puzzlebox.games.sudoku.SudokuMoves
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import com.ivandsky.puzzlebox.games.sudoku.engine.solve.SudokuSolver

/**
 * The Mini Sudoku practice board's rules (spec §2.2): where the board starts, how a move is judged against the easiest
 * rung of the hint ladder ([SudokuLadder]), and what Show me does. Pure: the play board owns the session.
 *
 * A practice board shows every open cell's candidates as notes, so the note-removing techniques can be played. The
 * notes it shows are the truth the ladder reads ([SudokuLadder.candidates]).
 */
object SudokuPractice {

    /**
     * One point of [walk]: the [boardBefore] the walk stood on and [rung], the id of the easiest rung there. Null when
     * no rung of the ladder applies (the walk is stuck, which a full board is not).
     */
    internal class WalkPoint(val boardBefore: SudokuBoard, val rung: TechniqueId?)

    /**
     * The puzzle walked from its givens as a practice plays it, one [WalkPoint] per deduction: every open cell carries
     * its candidates as notes, and at each point the walk makes the easiest rung's Show-me deduction (the cheapest of
     * that rung's, applied whole) and goes on. Givens stay givens; what the walk placed is an entry. The walk ends at the
     * full board, which is no point, or at a board no rung applies to, whose point has a null rung.
     *
     * [start] and [SudokuTechniqueWalker] both read it, so a technique has a practice exactly where the walk counts it.
     * The puzzle's stored solution is not checked here; the walk reads no solution, only the ladder.
     */
    internal fun walk(puzzle: SudokuPuzzle): Sequence<WalkPoint> {
        val board = SudokuBoard.of(puzzle)
        val candidates = SudokuLadder.candidates(puzzle, board)
        val noted = board.cells.indices.filter { board.cells[it] == SudokuCell.Empty }.fold(board) { acc, idx -> acc.with(idx, SudokuCell.Notes(candidates[idx])) }
        return walkFrom(puzzle, noted)
    }

    /** [walk]'s turns from [start], a board as a practice shows it: the points ahead of it, whatever it was played from. */
    private fun walkFrom(puzzle: SudokuPuzzle, start: SudokuBoard): Sequence<WalkPoint> = sequence {
        val geometry = puzzle.geometry
        var board = start
        // Each turn places a digit or takes a note off, and a board holds only cells * (digits + 1) of those.
        repeat(geometry.cellCount * (geometry.size + 1)) {
            if (board.isFull) return@sequence
            val rung = SudokuLadder.easiest(puzzle, board)
            yield(WalkPoint(board, rung?.id))
            if (rung == null) return@sequence
            board = commands(board, rung.steps.first()).fold(board) { acc, command -> command.apply(acc) }
        }
        error("the walk did not finish")
    }

    /** [puzzle]'s givens have exactly one solution and it is the stored one, so the judge's and Show me's reads of it are sound. */
    internal fun hasUniqueSolution(puzzle: SudokuPuzzle): Boolean {
        val result = SudokuSolver.solve(puzzle.geometry, puzzle.givens, limit = 2)
        return result.count == 1 && result.solution == puzzle.solution
    }

    /**
     * The board a practice of [kind] for [technique] starts on ([BoardKind]), from the points of [walk]. A focus board
     * opens at the start of the longest run of consecutive points where [technique] is the easiest rung (a tie: the
     * earliest). A review board treats [technique] and every rung before it in [SudokuTechniqueIds.all] as learned,
     * takes the run of consecutive learned points with the most [technique] points (a tie: the earliest), and opens at
     * that run's first such point. A point no rung applies to (the walk's stalled end) is no learned technique, so it
     * ends a run of either kind.
     * Givens stay givens; what the walk placed is an entry, and every open cell carries the candidates the walk left it
     * as notes. A rung with several deductions is walked by its cheapest.
     *
     * Null when [technique] is no Sudoku technique, when the puzzle's stored solution is not its unique solution
     * (the judge reads it), and when the walk is solved or stuck before [technique] is ever the easiest rung, for
     * either kind: so a technique has a board of each kind exactly where [SudokuTechniqueWalker] counts it.
     */
    fun start(puzzle: SudokuPuzzle, technique: TechniqueId, kind: BoardKind): SudokuBoard? {
        val trained = SudokuTechniqueIds.all.indexOf(technique)
        if (trained < 0 || !hasUniqueSolution(puzzle)) return null
        val learned = if (kind == BoardKind.Review) SudokuTechniqueIds.all.take(trained + 1).toSet() else setOf(technique)
        val inRun = { point: WalkPoint -> point.rung in learned }
        val runs = ArrayList<List<WalkPoint>>()
        var run = ArrayList<WalkPoint>()
        for (point in walk(puzzle)) {
            if (inRun(point)) {
                run += point
            } else if (run.isNotEmpty()) {
                runs += run
                run = ArrayList()
            }
        }
        if (run.isNotEmpty()) runs += run
        return runs.map { points -> points.filter { it.rung == technique } }.maxByOrNull { it.size }?.firstOrNull()?.boardBefore
    }

    /**
     * Whether a practice of [kind] for [technique] has nothing more to ask on [board] ([BoardKind]): the puzzle is
     * solved, no rung applies to it (stalled: the next move would be a reveal or a guess, which no technique finds), or
     * the easiest rung is another technique (focus) or a technique later than [technique] in [SudokuTechniqueIds.all]
     * (review). A review is also done once no [technique] step lies ahead of it ([trainedAhead]) and the player has made
     * [REVIEW_GRACE] moves since the last one: [movesSinceTrained] counts those, so the board does not run on through
     * the easier moves that are left. A focus board ignores it.
     */
    fun isDone(puzzle: SudokuPuzzle, board: SudokuBoard, technique: TechniqueId, kind: BoardKind, movesSinceTrained: Int = 0): Boolean {
        if (board.isFull) return true
        val rung = SudokuLadder.easiest(puzzle, board)?.id ?: return true
        return when (kind) {
            BoardKind.Focus -> rung != technique
            BoardKind.Review ->
                SudokuTechniqueIds.all.indexOf(rung) > SudokuTechniqueIds.all.indexOf(technique) ||
                    (movesSinceTrained >= REVIEW_GRACE && !trainedAhead(puzzle, board, technique))
        }
    }

    /** The moves a review board takes after its last [technique] step before it ends. */
    const val REVIEW_GRACE = 3

    /**
     * Whether the walk from [board] still reaches a [technique] step before it leaves what a review of [technique] has
     * learned (a later rung, or the end). The walk is the easiest-first one, so it is what Show me would play.
     */
    fun trainedAhead(puzzle: SudokuPuzzle, board: SudokuBoard, technique: TechniqueId): Boolean {
        val learned = SudokuTechniqueIds.all.take(SudokuTechniqueIds.all.indexOf(technique) + 1).toSet()
        return walkFrom(puzzle, board).takeWhile { it.rung in learned }.any { it.rung == technique }
    }

    /**
     * [move] on [before], judged. A placement fits when it is the cell's solution digit and a note removal when it is
     * not; a move that does not fit is Wrong. A fitting move that some deduction of the easiest rung makes is Right,
     * whichever instance the player found. A placement the easiest rung does not make is Fits when it is a single
     * ([SudokuLadder.isSingle]), whatever rung of the ladder it belongs to; any other move that fits, and a note removal
     * the rung does not make, is Easier. With no rung to judge by,
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
                else -> rung.steps.firstOrNull { it.places(move) }?.let { SudokuPracticeJudgement.Right(rung.id, it) }
                    ?: if (SudokuLadder.isSingle(puzzle, before, move.idx, move.digit)) SudokuPracticeJudgement.Fits(rung.id) else easier(rung)
            }
            is SudokuTrainerMove.RemoveNote -> when {
                move.digit == puzzle.solution[move.idx] -> wrong(SudokuPracticeJudgement.Reason.SolutionNote)
                rung == null -> wrong(SudokuPracticeJudgement.Reason.NotAMove)
                else -> rung.steps.firstOrNull { it.strikes(move) }?.let { SudokuPracticeJudgement.Right(rung.id, it) } ?: easier(rung)
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

    private fun easier(rung: SudokuLadder.Rung): SudokuPracticeJudgement = SudokuPracticeJudgement.Easier(rung.id, rung.steps.first())

    private fun SudokuStep.places(move: SudokuTrainerMove.Place): Boolean =
        this is SudokuStep.Single && place.cell == move.idx && place.digit == move.digit

    private fun SudokuStep.strikes(move: SudokuTrainerMove.RemoveNote): Boolean =
        this is SudokuStep.Elimination && (struck[move.idx] ?: 0) and (1 shl (move.digit - 1)) != 0
}
