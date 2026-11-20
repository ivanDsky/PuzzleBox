package com.ivandsky.puzzlebox.game.trainer

import com.ivandsky.puzzlebox.game.Puzzle

/**
 * One game's technique tally for a puzzle, along the practice board's walk (spec §2.2): from the givens, a
 * deduction of the easiest rung that applies at every point. The walker counts the points where each technique
 * was that easiest rung, so a technique is counted exactly when the game's practice board for it opens on the
 * puzzle. The index pass, the generator filter and the on-device puzzle check all reach a game's walker through
 * [com.ivandsky.puzzlebox.game.GamePlugin.techniqueWalker], so no caller names a game.
 *
 * A canonical [Puzzle] already embeds its solution, so the puzzle is the only input. Board-carrying
 * steps stay private to each game; this contract shows only ids and counts.
 */
fun interface TechniqueWalker {
    /**
     * Walk points per technique where it was the easiest rung, on the practice walk of [puzzle]. A technique
     * that never was is absent (no zero entries). A puzzle of another game, or one that does not decode, gives
     * an empty map. Never throws.
     */
    fun techniqueCounts(puzzle: Puzzle): Map<TechniqueId, Int>

    /**
     * Version of this walker's technique set: bump it when the set of ids it reports or the meaning
     * of an id changes. The index pass records it in every index it writes, so a shipped index
     * names the technique set it was built with.
     */
    val techniqueSetVersion: Int get() = 1
}
