package com.ivandsky.puzzlebox.games.tango.engine.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoTestSupport
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.editor.TangoDailyBoards
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraft
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRung
import com.ivandsky.puzzlebox.games.tango.tangoPlugin
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TangoTechniqueWalkerTest {

    @Test
    fun `a technique is counted exactly when its practice board opens, on every archive daily`() {
        val techniques = TangoStepRung.entries.map { it.trainerId }
        val dailies = TangoDailyBoards.all.map { draft ->
            TangoPuzzle(TangoDraft.variantOf(draft.n), draft.n, draft.givens, draft.markers)
        }
        assertEquals(756, dailies.size)
        for ((i, puzzle) in dailies.withIndex()) {
            val solution = TangoUniqueness.solve(puzzle)!!
            val counts = TangoTechniqueWalker.techniqueCounts(canonical(puzzle))
            assertTrue("daily $i: no zero entries", counts.values.all { it > 0 })
            for (technique in techniques) {
                assertEquals(
                    "daily $i ${technique.value}",
                    TangoPractice.start(puzzle, solution, technique) != null,
                    (counts[technique] ?: 0) > 0,
                )
            }
        }
    }

    @Test
    fun `a count is the points where the technique was the easiest rung`() {
        for ((name, expected) in EXACT_COUNTS) {
            val puzzle = dailies.getValue(name)
            val counts = tangoPlugin.techniqueWalker!!.techniqueCounts(canonical(puzzle))
            assertEquals(name, expected.mapKeys { TechniqueId(it.key) }, counts)
        }
    }

    @Test
    fun `the walk starts on the givens and each point is one deduction`() {
        for ((name, puzzle) in dailies) {
            val solution = TangoUniqueness.solve(puzzle)!!
            val points = TangoPractice.walk(puzzle, solution).toList()
            val counts = TangoTechniqueWalker.techniqueCounts(canonical(puzzle))
            assertEquals(name, List(puzzle.cellCount) { puzzle.givens[it] }, points.first().board)
            assertEquals(name, points.size, counts.values.sum())
            assertTrue(name, points.zipWithNext().all { (a, b) -> b.board.count { it != null } > a.board.count { it != null } })
        }
    }

    @Test
    fun `foreign, undecodable and ambiguous puzzles count nothing`() {
        val real = dailies.getValue("byid-1")
        val bytes = TangoCodec.encode(real)
        assertEquals(emptyMap<TechniqueId, Int>(), TangoTechniqueWalker.techniqueCounts(Puzzle(GameId("zip"), real.variant, TangoCodec.VERSION, bytes)))
        assertEquals(emptyMap<TechniqueId, Int>(), TangoTechniqueWalker.techniqueCounts(Puzzle(TangoDefinition.id, real.variant, TangoCodec.VERSION, byteArrayOf(1, 2, 3))))
        val ambiguous = TangoPuzzle(variant = real.variant, n = real.n, givens = mapOf(0 to TangoCell.SUN), markers = emptyList())
        assertEquals(null, TangoUniqueness.solve(ambiguous))
        assertEquals(emptyMap<TechniqueId, Int>(), TangoTechniqueWalker.techniqueCounts(canonical(ambiguous)))
    }

    @Test
    fun `the plugin carries the walker`() {
        assertNotNull(tangoPlugin.techniqueWalker)
        assertEquals(2, tangoPlugin.techniqueWalker!!.techniqueSetVersion)
    }

    private companion object {
        val dailies: Map<String, TangoPuzzle> by lazy {
            mapOf(
                "byid-1" to TangoTestSupport.dailyOf("linkedin-tango-byid-1.json"),
                "byid-301" to TangoTestSupport.dailyOf("linkedin-tango-byid-301.json"),
                "byid-758" to TangoTestSupport.dailyOf("linkedin-tango-byid-758.json"),
            )
        }

        val EXACT_COUNTS: Map<String, Map<String, Int>> = mapOf(
            "byid-1" to mapOf("sign" to 8, "pair" to 6, "sandwich" to 5, "full-line" to 8),
            "byid-301" to mapOf("sign" to 4, "pair" to 10, "sandwich" to 2, "full-line" to 6),
            "byid-758" to mapOf(
                "sign" to 4, "pair" to 9, "sandwich" to 1, "full-line" to 5, "equal-pair" to 1, "count-squeeze" to 2,
            ),
        )

        fun canonical(puzzle: TangoPuzzle): Puzzle =
            (TangoDefinition.decode(TangoCodec.encode(puzzle)) as DecodeResult.Ok).value
    }
}
