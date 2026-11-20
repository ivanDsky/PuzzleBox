package com.ivandsky.puzzlebox.games.tango.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoCountSqueezeRule
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoEqualPairRule
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoLegacyStepRules
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoSignCountRule
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoSplitPairRule
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRule
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRung

/**
 * Judges one snippet move against the snippet's target technique (spec A4): [TrainerVerdict.Right]
 * when that technique's rung proves the move on the board, [TrainerVerdict.AnotherWay] when the move
 * fits the solution all the same, [TrainerVerdict.Wrong] when it breaks it.
 *
 * "Proves" reads the rung's full [com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRule.forced],
 * every deduction it can make on the board, not only the one the stepper would show.
 */
object TangoMoveCheck {

    /** [judge]'s verdict alone. */
    fun classify(
        puzzle: TangoPuzzle,
        solution: List<TangoCell>,
        gridBefore: List<TangoCell?>,
        technique: TechniqueId,
        move: TangoTrainerMove,
    ): TrainerVerdict = judge(puzzle, solution, gridBefore, technique, move).verdict

    /**
     * [move] on [gridBefore], judged for [technique]. [solution] is the puzzle's unique solution,
     * which the caller carries from the walk that produced the board (the payload holds none). A
     * Right move comes with the deduction that proves it, which need not be the step a snippet
     * was cut at: the player may find another instance of the technique.
     *
     * @throws IllegalArgumentException when [technique] is no Tango technique, or [move]'s cell is
     *   off the board, a given, or already filled.
     */
    fun judge(
        puzzle: TangoPuzzle,
        solution: List<TangoCell>,
        gridBefore: List<TangoCell?>,
        technique: TechniqueId,
        move: TangoTrainerMove,
    ): TangoMoveJudgement {
        val rung = requireNotNull(TangoStepRung.entries.firstOrNull { it.trainerId == technique }) {
            "$technique is not a Tango technique"
        }
        require(gridBefore.size == puzzle.cellCount && solution.size == puzzle.cellCount) {
            "grid ${gridBefore.size} and solution ${solution.size} do not match ${puzzle.cellCount} cells"
        }
        require(move.idx in 0 until puzzle.cellCount && !puzzle.isGiven(move.idx) && gridBefore[move.idx] == null) {
            "$move is off the board or on a cell that is a given or already filled"
        }
        // A rung proves only what holds in the solution, so a move that breaks it is never proved.
        if (move.value != solution[move.idx]) return TangoMoveJudgement(TrainerVerdict.Wrong, proof = null)
        // Several deductions may prove the cell; the one reading the fewest cells is the simplest.
        val proof = ruleOf(rung).forced(puzzle, gridBefore)
            .filter { it.idx == move.idx && it.value == move.value }
            .minByOrNull { it.evidence.size }
            ?: return TangoMoveJudgement(TrainerVerdict.AnotherWay, proof = null)
        return TangoMoveJudgement(TrainerVerdict.Right, proof)
    }

    /** The rule behind [rung]: the same objects the stepper lists, each of which returns every deduction it can make. */
    private fun ruleOf(rung: TangoStepRung): TangoStepRule = when (rung) {
        TangoStepRung.SIGN -> TangoLegacyStepRules.sign
        TangoStepRung.PAIR -> TangoLegacyStepRules.pair
        TangoStepRung.SANDWICH -> TangoLegacyStepRules.sandwich
        TangoStepRung.FULL_LINE -> TangoLegacyStepRules.fullLine
        TangoStepRung.EQUAL_PAIR -> TangoEqualPairRule
        TangoStepRung.SIGN_COUNT -> TangoSignCountRule
        TangoStepRung.SPLIT_PAIR -> TangoSplitPairRule
        TangoStepRung.COUNT_SQUEEZE -> TangoCountSqueezeRule
        TangoStepRung.LINE_OPTIONS -> TangoLegacyStepRules.lineOptions
    }
}
