package com.ivandsky.puzzlebox.games.queens.engine.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TechniqueWalker
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec

/**
 * The Queens [TechniqueWalker]: the walk a practice board plays ([QueensPractice.walk]), counted. A puzzle is solved
 * from the empty board with the easiest rung's deduction at every point, Auto-cross on (A11), and a technique's count
 * is the number of points where its rung was the easiest one with a deduction. So a technique is counted exactly when
 * [QueensPractice.start] has a board for it: every puzzle the index or the generated set lists for a technique opens
 * a practice. Version 2: version 1 counted the hint stepper's own steps, which defer what a player sees at a glance.
 */
object QueensTechniqueWalker : TechniqueWalker {

    override val techniqueSetVersion: Int = 2

    /** Empty for another game's puzzle, one that does not decode, or one without a true solution to walk by. */
    override fun techniqueCounts(puzzle: Puzzle): Map<TechniqueId, Int> {
        if (puzzle.gameId != QueensDefinition.id) return emptyMap()
        val decoded = (QueensCodec.decode(puzzle.payload) as? DecodeResult.Ok)?.value ?: return emptyMap()
        return QueensPractice.walk(decoded).mapNotNull { (_, easiest) -> easiest }.groupingBy { it }.eachCount()
    }
}
