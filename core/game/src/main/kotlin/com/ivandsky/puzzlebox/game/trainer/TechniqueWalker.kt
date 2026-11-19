package com.ivandsky.puzzlebox.game.trainer

import com.ivandsky.puzzlebox.game.Puzzle

/**
 * One game's technique tally for a puzzle: the stepper solves [Puzzle] from its givens and the
 * walker counts the steps it takes per technique. The index pass, the generator filter and
 * on-device snippet building all reach a game's walker through [com.ivandsky.puzzlebox.game.GamePlugin.techniqueWalker],
 * so no caller names a game.
 *
 * A canonical [Puzzle] already embeds its solution, so the puzzle is the only input. Board-carrying
 * steps stay private to each game; this contract shows only ids and counts. The walk follows the
 * stepper's own choice at every step.
 */
fun interface TechniqueWalker {
    /**
     * Steps taken per technique when the stepper solves [puzzle]. A technique the walk never used is
     * absent (no zero entries). A puzzle of another game, or one that does not decode, gives an empty
     * map. Never throws.
     */
    fun techniqueCounts(puzzle: Puzzle): Map<TechniqueId, Int>
}
