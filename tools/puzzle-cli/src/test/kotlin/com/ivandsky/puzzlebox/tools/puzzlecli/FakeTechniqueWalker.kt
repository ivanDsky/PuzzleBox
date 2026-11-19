package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TechniqueWalker

/** Counts `singles` as the payload byte (the puzzle's number) and finds nothing in a puzzle whose byte is 0. */
class FakeTechniqueWalker(override val techniqueSetVersion: Int = 3) : TechniqueWalker {
    override fun techniqueCounts(puzzle: Puzzle): Map<TechniqueId, Int> {
        val count = puzzle.payload[0].toInt()
        return if (count == 0) emptyMap() else mapOf(TechniqueId("singles") to count)
    }
}
