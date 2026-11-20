package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensGenerator
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import kotlin.random.Random

/**
 * Seeded random Queens boards with exactly one solution, from the engine's own [QueensGenerator], at a board size drawn
 * from [SIZES]: the sizes the daily has come in, all inside the registered `QueensVariants` range.
 */
object QueensDrillGenerator : DrillGenerator {

    /** Daily boards run from 7×7 (no. 916) up to 10×10; a smaller board rarely needs the harder rungs. */
    val SIZES: IntRange = 7..10

    override fun next(random: Random): Puzzle? {
        val puzzle = QueensGenerator(random).nextUnique(SIZES.random(random)) ?: return null
        return (QueensDefinition.decode(QueensCodec.encode(puzzle)) as? DecodeResult.Ok)?.value
    }
}
