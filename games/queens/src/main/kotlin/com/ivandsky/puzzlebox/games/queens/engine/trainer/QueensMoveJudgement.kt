package com.ivandsky.puzzlebox.games.queens.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep

/** What [QueensMoveCheck] says of a move: its [verdict], and when that is Right the [proof] step that proves the move, else null. */
data class QueensMoveJudgement(val verdict: TrainerVerdict, val proof: QueensStep.Deduction?)
