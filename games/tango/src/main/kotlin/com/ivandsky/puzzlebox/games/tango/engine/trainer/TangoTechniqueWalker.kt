package com.ivandsky.puzzlebox.games.tango.engine.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TechniqueWalker
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness

/**
 * The Tango [TechniqueWalker]: it walks a puzzle the way the practice board does ([TangoPractice.walk]: from the
 * givens, one whole deduction of the easiest ladder rung at every point) and counts, per technique id, the points where
 * that technique was the easiest rung. A reveal counts for nothing.
 *
 * So a technique is counted exactly when [TangoPractice.start] has a board for it: every puzzle the technique index or
 * the generated set lists for a technique opens a practice board for it.
 */
object TangoTechniqueWalker : TechniqueWalker {

    /**
     * Version 2: counts follow the practice walk (the easiest rung at every point). Version 1 counted the stepper's own
     * steps, which defer a step the player sees at a glance, so a listed technique could have no practice board.
     */
    override val techniqueSetVersion: Int get() = 2

    /** Empty for another game's puzzle, one that does not decode, or one without a unique solution. */
    override fun techniqueCounts(puzzle: Puzzle): Map<TechniqueId, Int> {
        if (puzzle.gameId != TangoDefinition.id) return emptyMap()
        val decoded = (TangoCodec.decode(puzzle.payload) as? DecodeResult.Ok)?.value ?: return emptyMap()
        val solution = TangoUniqueness.solve(decoded) ?: return emptyMap()
        return TangoPractice.walk(decoded, solution).mapNotNull { it.technique }.groupingBy { it }.eachCount()
    }
}
