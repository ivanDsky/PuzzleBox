package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason
import com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHint
import com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHints
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec S6 for Queens (A8, A9, ticket 11): a Unique board's difficulty is the level of the
 * hardest [com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder.Default]
 * rung its solve needs, one fixture per label; a Unique board the ladder cannot finish needs
 * guessing, has no difficulty and is blocked from publishing.
 */
class QueensDifficultyTest {

    private val labels = listOf(
        "queens-difficulty-easy.json" to Difficulty.Level.Easy,
        "queens-difficulty-medium.json" to Difficulty.Level.Medium,
        "queens-difficulty-hard.json" to Difficulty.Level.Hard,
        "queens-difficulty-expert.json" to Difficulty.Level.Expert,
    )

    @Test
    fun `each label fixture is Unique with its exact difficulty level and no guessing block`() {
        labels.forEach { (name, level) ->
            val draft = draft(name)

            val verdict = QueensDraftAnalyzer.analyze(draft, Budget.Unlimited) as Verdict.Unique

            assertEquals(name, Solution(json(name).ints("solution")), verdict.solution)
            assertEquals(name, level, verdict.difficulty?.level)
            assertFalse(name, verdict.needsGuessing)
            assertEquals(name, emptyList<PublishBlock>(), QueensPublishGates.blocks(draft, verdict))
        }
    }

    @Test
    fun `the guessing fixture is Unique needing guessing, without difficulty, and blocked`() {
        val name = "queens-difficulty-guessing.json"
        val draft = draft(name)

        val verdict = QueensDraftAnalyzer.analyze(draft, Budget.Unlimited)

        assertEquals(
            Verdict.Unique(Solution(json(name).ints("solution")), difficulty = null, needsGuessing = true),
            verdict,
        )
        val blocks = QueensPublishGates.blocks(draft, verdict).map { it.id }
        assertTrue(blocks.toString(), PublishBlock.NEEDS_GUESSING in blocks)
    }

    @Test
    fun `two runs give identical verdicts and blocks`() {
        (labels.map { it.first } + "queens-difficulty-guessing.json").forEach { name ->
            val draft = draft(name)
            val first = QueensDraftAnalyzer.analyze(draft, Budget.Unlimited)
            val second = QueensDraftAnalyzer.analyze(draft, Budget.Unlimited)

            assertEquals(name, first, second)
            assertEquals(name, QueensPublishGates.blocks(draft, first), QueensPublishGates.blocks(draft, second))
        }
    }

    @Test
    fun `a Unique verdict that needs no guessing adds no guessing block`() {
        val draft = draft("queens-difficulty-guessing.json")

        val blocks = QueensPublishGates.blocks(draft, Verdict.Unique(Solution(json("queens-difficulty-guessing.json").ints("solution"))))

        assertNull(blocks.firstOrNull { it.id == PublishBlock.NEEDS_GUESSING })
    }

    private fun draft(name: String): QueensDraft = json(name).let { QueensDraft(it.int("size"), it.ints("regions")) }

    @Test
    fun `a hint-driven solve of each label fixture reasons every hint and peaks at its label`() {
        labels.forEach { (name, level) ->
            val reasons = hintedSolve(name)

            assertTrue(name, reasons.all { it != null })
            assertEquals(name, level, reasons.maxOf { it!!.rung.level })
        }
    }

    @Test
    fun `a hint-driven solve of the guessing fixture falls back to an unreasoned hint`() {
        assertTrue(hintedSolve("queens-difficulty-guessing.json").any { it == null })
    }

    /** Places each hinted cell in turn from the empty board; the reason of every hint. */
    private fun hintedSolve(name: String): List<HintReason?> {
        val o = json(name)
        val puzzle = QueensPuzzle(o.int("size"), o.ints("regions"), solution = o.ints("solution").map(::Cell))
        val placed = mutableListOf<Cell>()
        val reasons = mutableListOf<HintReason?>()
        while (true) {
            val hint = QueensHints().hint(puzzle, placed) as? QueensHint.Region ?: return reasons
            reasons += hint.reason
            placed += hint.cell
        }
    }

    private fun json(name: String): JsonObject =
        Json.parseToJsonElement(
            QueensDifficultyTest::class.java.getResourceAsStream("/fixtures/$name")!!.readBytes().decodeToString(),
        ).jsonObject

    private fun JsonObject.int(key: String): Int = this[key]!!.jsonPrimitive.content.toInt()

    private fun JsonObject.ints(key: String): List<Int> = this[key]!!.jsonArray.map { it.jsonPrimitive.content.toInt() }
}
