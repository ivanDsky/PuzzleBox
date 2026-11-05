package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef

/**
 * One lesson of a game's tutorial (spec A11/A12): a short [instruction] line that teaches
 * at most one rule, and the tiny [puzzle] that shows it. The game hand-authors both in
 * its UI module. [ref] is the identity the sandboxed controller adopts the puzzle under;
 * tutorial refs are custom, non-daily ones (no puzzle number), and no record is ever
 * written for them.
 */
class TutorialStep(val instruction: String, val puzzle: Puzzle, val ref: PuzzleRef)
