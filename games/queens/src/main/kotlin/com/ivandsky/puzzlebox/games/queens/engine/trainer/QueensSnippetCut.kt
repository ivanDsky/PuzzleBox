package com.ivandsky.puzzlebox.games.queens.engine.trainer

import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep

/**
 * One Trainer snippet cut from a walk: [boardBefore], the board just before [step], the deduction
 * of the target technique the player is asked to find. The board is as the walk had it, with
 * Auto-cross on (A11): its queens, and the crosses the walk drew by hand in its manual set; the
 * crosses the queens force are the board's to derive.
 */
data class QueensSnippetCut(val boardBefore: QueensBoard, val step: QueensStep.Deduction)
