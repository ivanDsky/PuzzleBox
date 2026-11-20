package com.ivandsky.puzzlebox.games.queens.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensCandidates
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensDeduction
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.toStep

/**
 * Judges one snippet move against the snippet's target technique (spec A4): [TrainerVerdict.Right]
 * when that technique's rung proves the move on the board, [TrainerVerdict.AnotherWay] when the move
 * fits the solution all the same, [TrainerVerdict.Wrong] when it breaks it.
 *
 * "Proves" reads the rung's full [com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensRung.steps],
 * not the stepper's simplest ones: a look-ahead the player finds at any depth the rung covers is
 * that technique, not only the shallowest the stepper would show.
 */
object QueensMoveCheck {

    /** [judge]'s verdict alone. */
    fun classify(puzzle: QueensPuzzle, boardBefore: QueensBoard, technique: TechniqueId, move: QueensTrainerMove): TrainerVerdict =
        judge(puzzle, boardBefore, technique, move).verdict

    /**
     * [move] on [boardBefore], judged for [technique]. The board reads as a snippet board shows it
     * (A11): its queens with the crosses they force, plus its manual crosses, the candidates the
     * stepper builds with Auto-cross on. [puzzle] carries the solution the fit is judged against.
     * A Right move comes with the step that proves it, which need not be the step a
     * snippet was cut at: the player may find another instance of the technique.
     *
     * @throws IllegalArgumentException when [technique] is no Queens technique, [puzzle] has no
     *   solution, or [move]'s cell is off the board or already holds a queen or a cross.
     */
    fun judge(puzzle: QueensPuzzle, boardBefore: QueensBoard, technique: TechniqueId, move: QueensTrainerMove): QueensMoveJudgement {
        val rung = requireNotNull(QueensLadder.Stepwise.rungs.firstOrNull { it.trainerId == technique }) {
            "$technique is not a Queens technique"
        }
        val solution = requireNotNull(puzzle.solution) { "a move check needs the puzzle's solution" }
        val queens = boardBefore.queens.mapTo(HashSet(boardBefore.queens.size * 2)) { it.idx }
        val crosses = boardBefore.manual.mapNotNullTo(HashSet(boardBefore.manual.size * 2)) { cell -> cell.idx.takeIf { it !in queens } }
        val candidates = QueensCandidates.start(puzzle, queens).exclude(crosses)
        val cell = move.cell.idx
        require(cell in 0 until puzzle.cellCount && candidates.isOpen(cell)) {
            "$move is off the board or on a cell that already holds a queen or a cross"
        }
        val fits = when (move) {
            is QueensTrainerMove.Place -> move.cell in solution
            is QueensTrainerMove.Cross -> move.cell !in solution
        }
        // A rung proves only what holds in every solution of the board, so a move that breaks this one is never proved.
        if (!fits) return QueensMoveJudgement(TrainerVerdict.Wrong, proof = null)
        // Several proofs may prove the move; the one with the least evidence is the simplest.
        val proof = rung.steps(puzzle, candidates)
            .filter { p ->
                when (val d = p.deduction) {
                    is QueensDeduction.Place -> move is QueensTrainerMove.Place && d.cell == cell
                    is QueensDeduction.Exclude -> move is QueensTrainerMove.Cross && cell in d.cells
                }
            }
            .minByOrNull { it.evidence.size }
            ?: return QueensMoveJudgement(TrainerVerdict.AnotherWay, proof = null)
        return QueensMoveJudgement(TrainerVerdict.Right, proof.toStep())
    }
}
