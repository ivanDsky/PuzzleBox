package com.ivandsky.puzzlebox.games.queens.engine.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensTestSupport
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder
import com.ivandsky.puzzlebox.games.queens.queensPlugin
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Counts are the points of the practice walk where a rung was the easiest one, and a technique is counted exactly
 * when a practice opens on it: pinned on the three dailies and on every 25th board of the hint corpus.
 */
class QueensTechniqueWalkerTest {

    private fun id(name: String) = TechniqueId(name)

    @Test
    fun `a technique's count is the number of points where its rung was the easiest`() {
        assertEquals(
            mapOf(id("singles") to 7, id("line-lock") to 1),
            QueensTechniqueWalker.techniqueCounts(canonical(dailies.getValue("real-916"))),
        )
        assertEquals(
            mapOf(
                id("singles") to 8, id("line-lock") to 3, id("shared-squares") to 2, id("pigeonhole-regions") to 1,
                id("pigeonhole-lines") to 1, id("squeeze") to 2, id("count-squeeze") to 8,
                id("short-look-ahead") to 12, id("long-look-ahead") to 10,
            ),
            QueensTechniqueWalker.techniqueCounts(canonical(dailies.getValue("unique"))),
        )
        assertEquals(
            mapOf(id("pigeonhole-regions") to 2),
            QueensTechniqueWalker.techniqueCounts(canonical(dailies.getValue("sample"))),
        )
        for ((name, puzzle) in dailies + corpus) {
            val counts = QueensTechniqueWalker.techniqueCounts(canonical(puzzle))
            val walked = QueensPractice.walk(puzzle).mapNotNull { it.second }.toList()
            assertEquals(name, walked.groupingBy { it }.eachCount(), counts)
            assertTrue(name, counts.values.all { it > 0 })
        }
    }

    @Test
    fun `a technique is counted exactly when a practice opens on it`() {
        val techniques = QueensLadder.Stepwise.rungs.mapNotNull { it.trainerId }
        for ((name, puzzle) in dailies + corpus) {
            val counts = QueensTechniqueWalker.techniqueCounts(canonical(puzzle))
            assertTrue(name, counts.isNotEmpty())
            for (technique in techniques) {
                assertEquals("$name ${technique.value}", technique in counts, QueensPractice.start(puzzle, technique) != null)
            }
        }
    }

    @Test
    fun `the plugin carries the walker and counts from the canonical puzzle`() {
        val walker = queensPlugin.techniqueWalker
        assertNotNull(walker)
        for ((name, puzzle) in dailies) {
            assertEquals(name, QueensTechniqueWalker.techniqueCounts(canonical(puzzle)), walker!!.techniqueCounts(canonical(puzzle)))
        }
    }

    @Test
    fun `foreign, undecodable and unsolved puzzles count nothing`() {
        val real = dailies.getValue("real-916")
        val bytes = QueensCodec.encode(real)
        val variant = queensVariantId(real.size)
        assertEquals(emptyMap<TechniqueId, Int>(), QueensTechniqueWalker.techniqueCounts(Puzzle(GameId("zip"), variant, QueensCodec.VERSION, bytes)))
        assertEquals(emptyMap<TechniqueId, Int>(), QueensTechniqueWalker.techniqueCounts(Puzzle(QueensDefinition.id, variant, QueensCodec.VERSION, byteArrayOf(1, 2, 3))))
        assertEquals(emptyMap<TechniqueId, Int>(), QueensTechniqueWalker.techniqueCounts(canonical(real.copy(solution = null))))
        val broken = real.copy(solution = (0 until real.size).map(::Cell))
        assertEquals(emptyMap<TechniqueId, Int>(), QueensTechniqueWalker.techniqueCounts(canonical(broken)))
    }

    private companion object {
        val dailies: Map<String, QueensPuzzle> by lazy {
            mapOf(
                "real-916" to QueensTestSupport.daily("queens-daily-real-916.json"),
                "unique" to QueensTestSupport.daily("queens-daily-unique.json"),
                "sample" to QueensTestSupport.daily("queens-daily-sample.json"),
            )
        }

        /** Every 25th board of the hint corpus, solution and all. */
        val corpus: Map<String, QueensPuzzle> by lazy {
            val text = QueensTechniqueWalkerTest::class.java.getResourceAsStream("/fixtures/queens-hint-corpus.json")!!.readBytes().decodeToString()
            Json.parseToJsonElement(text).jsonArray.filterIndexed { i, _ -> i % 25 == 0 }.associate { record ->
                val board = record.jsonObject
                board.getValue("id").jsonPrimitive.content to QueensPuzzle(
                    size = board.getValue("size").jsonPrimitive.content.toInt(),
                    regions = board.getValue("regions").jsonArray.map { it.jsonPrimitive.content.toInt() },
                    solution = board.getValue("solution").jsonArray.map { Cell(it.jsonPrimitive.content.toInt()) },
                )
            }
        }

        fun canonical(puzzle: QueensPuzzle): Puzzle =
            (QueensDefinition.decode(QueensCodec.encode(puzzle)) as DecodeResult.Ok).value
    }
}
