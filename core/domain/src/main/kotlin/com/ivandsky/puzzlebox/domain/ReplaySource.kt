package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.model.SolveReplay

/** A replay's bytes with the puzzle they were played on. */
data class ReplaySource(val puzzle: Puzzle, val bytes: SolveReplay)
