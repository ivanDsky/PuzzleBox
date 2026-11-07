package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.codec.QueensWireParser
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec S4 for Queens (A8, ticket 10): the verdict carries its witnesses. A known 2-solution
 * board gives both solutions and exactly the cells they differ on; a board with a structural
 * clash gives exactly the clashing cells; the unique daily gives its stored solution.
 */
class QueensVerdictFixturesTest {

    @Test
    fun `the two-solution fixture is Multiple with both stored solutions and their differing cells`() {
        val o = json("queens-editor-two-solutions.json")

        val verdict = QueensDraftAnalyzer.analyze(QueensDraft(o.int("size"), o.ints("regions")), Budget.Unlimited)

        assertEquals(
            Verdict.Multiple(
                solutionA = Solution(o.ints("solutionA")),
                solutionB = Solution(o.ints("solutionB")),
                differingCells = o.ints("differingCells").toSet(),
            ),
            verdict,
        )
    }

    @Test
    fun `the clash fixture is None with exactly the stored clash cells`() {
        val o = json("queens-editor-clash.json")

        val verdict = QueensDraftAnalyzer.analyze(QueensDraft(o.int("size"), o.ints("regions")), Budget.Unlimited)

        assertEquals(Verdict.None(o.ints("conflicts").toSet()), verdict)
    }

    @Test
    fun `the unique daily is Unique with its stored solution`() {
        val puzzle = daily("queens-daily-unique.json")

        val verdict = QueensDraftAnalyzer.analyze(QueensDraft(puzzle.size, puzzle.regions), Budget.Unlimited)

        assertEquals(Solution(puzzle.solution!!.map { it.idx }.sorted()), (verdict as Verdict.Unique).solution)
    }

    @Test
    fun `a column band holding more whole regions than its width names those regions' cells`() {
        // Columns 1–2 wholly hold regions 0, 1 and 2; region 3 spans the rest.
        val regions = listOf(
            3, 0, 0, 3,
            3, 0, 1, 3,
            3, 2, 1, 3,
            3, 2, 2, 3,
        )

        assertEquals(setOf(1, 2, 5, 6, 9, 10, 13, 14), QueensClashLocator.conflicts(QueensPuzzle(4, regions)))
    }

    @Test
    fun `two diagonally touching 1-cell regions are a clash of those two cells`() {
        // Regions 0 (cell 0) and 1 (cell 5) are single cells touching on the diagonal.
        val regions = listOf(
            0, 2, 2, 2,
            2, 1, 3, 3,
            2, 3, 3, 3,
            2, 3, 3, 3,
        )

        assertEquals(setOf(0, 5), QueensClashLocator.conflicts(QueensPuzzle(4, regions)))
    }

    @Test
    fun `a board without a structural clash has no conflicts`() {
        val o = json("queens-editor-two-solutions.json")

        assertEquals(emptySet<Int>(), QueensClashLocator.conflicts(QueensPuzzle(o.int("size"), o.ints("regions"))))
    }

    private fun resource(name: String): String =
        QueensVerdictFixturesTest::class.java.getResourceAsStream("/fixtures/$name")!!.readBytes().decodeToString()

    private fun json(name: String): JsonObject = Json.parseToJsonElement(resource(name)).jsonObject

    private fun daily(name: String): QueensPuzzle =
        when (val r = QueensWireParser.parse(resource(name), LocalDate.of(2026, 11, 2))) {
            is DecodeResult.Ok -> r.value.puzzle
            is DecodeResult.Err -> throw AssertionError("fixture parse failed: ${r.error}")
        }

    private fun JsonObject.int(key: String): Int = this[key]!!.jsonPrimitive.content.toInt()

    private fun JsonObject.ints(key: String): List<Int> = this[key]!!.jsonArray.map { it.jsonPrimitive.content.toInt() }
}
