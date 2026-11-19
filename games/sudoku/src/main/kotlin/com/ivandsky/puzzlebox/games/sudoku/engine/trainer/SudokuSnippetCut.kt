package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep

/**
 * One Trainer snippet cut from a walk: [boardBefore], the board just before [step], the step of the
 * target technique the player is asked to find. The board has the givens and every cell the walk
 * filled so far; its candidates are the engine's, minus [struckBefore] (cell to digit mask), the
 * candidates earlier eliminations of the walk struck.
 */
data class SudokuSnippetCut(val boardBefore: SudokuBoard, val struckBefore: Map<Int, Int>, val step: SudokuStep)
