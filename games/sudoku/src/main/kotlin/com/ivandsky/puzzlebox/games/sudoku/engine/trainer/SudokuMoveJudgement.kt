package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep

/** What [SudokuMoveCheck] says of a move: its [verdict], and when that is Right the [proof] step that proves the move, else null. */
data class SudokuMoveJudgement(val verdict: TrainerVerdict, val proof: SudokuStep?)
