package com.ivandsky.puzzlebox.games.tango.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep

/** What [TangoMoveCheck] says of a move: its [verdict], and when that is Right the [proof] deduction that proves the move, else null. */
data class TangoMoveJudgement(val verdict: TrainerVerdict, val proof: TangoStep.Deduction?)
