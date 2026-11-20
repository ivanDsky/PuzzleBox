package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TechniqueWalker
import com.ivandsky.puzzlebox.model.GameId

/**
 * A made-up game for the `generate-drills` tests. A puzzle's payload is `[aSteps, bSteps, salt]`: the walk counts
 * technique [A] as byte 0 and [B] as byte 1 (nothing for a 0), [C] never. [B] ranks above [A] in the families, so an
 * [A] drill may show no [B]. The generator draws all three bytes from the random, so a seed fixes the candidates.
 */
object DrillGameFixtures {
    val id = GameId("fakegame")
    val A = TechniqueId("a")
    val B = TechniqueId("b")
    val C = TechniqueId("c")

    fun puzzle(a: Int, b: Int, salt: Int): Puzzle =
        Puzzle(id, VariantId("fake/v1"), 1, byteArrayOf(a.toByte(), b.toByte(), salt.toByte()))

    class Walker(override val techniqueSetVersion: Int = 3) : TechniqueWalker {
        override fun techniqueCounts(puzzle: Puzzle): Map<TechniqueId, Int> = buildMap {
            if (puzzle.payload[0] > 0) put(A, puzzle.payload[0].toInt())
            if (puzzle.payload[1] > 0) put(B, puzzle.payload[1].toInt())
        }
    }

    val generator = DrillGenerator { random -> puzzle(random.nextInt(0, 4), random.nextInt(0, 3), random.nextInt(256)) }

    fun game(
        generator: DrillGenerator = this.generator,
        techniques: List<TechniqueId> = listOf(A, B, C),
        accepts: (Puzzle, TechniqueId) -> Boolean = { _, _ -> true },
    ): DrillGame = DrillGame(id, techniques, Walker(), generator, families = mapOf(A to 0, B to 1), accepts = accepts)

    /** A second made-up game, `othergame`, whose puzzles carry its own id and always show technique [A]. */
    fun other(): DrillGame {
        val other = GameId("othergame")
        return DrillGame(other, listOf(A), Walker(), { random ->
            Puzzle(other, VariantId("fake/v1"), 1, byteArrayOf(random.nextInt(1, 4).toByte(), 0, random.nextInt(256).toByte()))
        })
    }
}
