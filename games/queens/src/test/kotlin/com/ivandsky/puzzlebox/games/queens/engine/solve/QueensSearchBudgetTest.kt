package com.ivandsky.puzzlebox.games.queens.engine.solve

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraftAnalyzer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ticket 04 (spec A7, S5 JVM half): the search polls its [Budget] inside the DFS and answers
 * [QueensSearchOutcome.Exhausted] once it is spent, so the editor's 1.5 s bound holds on the
 * committed worst-case 12×12 board; [Budget.Unlimited] leaves daily play's results unchanged.
 */
class QueensSearchBudgetTest {

    @Test
    fun `the worst-case editor board settles within the editor budget`() {
        val worst = worstCase()
        val start = System.nanoTime()

        val outcome = QueensSearch.bounded(worst, limit = 2, budget = Budget.within(BUDGET_MS, System::nanoTime))

        val elapsedMs = (System.nanoTime() - start) / NANOS_PER_MILLI
        assertTrue("took $elapsedMs ms", elapsedMs <= BUDGET_MS + SLACK_MS)
        assertTrue(outcome is QueensSearchOutcome.Done || outcome == QueensSearchOutcome.Exhausted)
    }

    @Test
    fun `a cancelled budget is Exhausted at the first poll`() {
        var polls = 0
        val cancelled = Budget { polls++; true }

        val outcome = QueensSearch.bounded(worstCase(), limit = 2, budget = cancelled)

        assertEquals(QueensSearchOutcome.Exhausted, outcome)
        assertEquals(1, polls)
    }

    @Test
    fun `a budget spent mid-search stops the search with Exhausted`() {
        var polls = 0
        val spentAfterTen = Budget { ++polls > 10 }

        val outcome = QueensSolver().solutionsWithin(worstCase(), limit = 2, budget = spentAfterTen)

        assertEquals(QueensSearchOutcome.Exhausted, outcome)
        assertEquals(11, polls)
    }

    @Test
    fun `the analyzer reads a budget spent mid-search as Undetermined`() {
        var polls = 0
        val spentAfterTen = Budget { ++polls > 10 }
        val worst = worstCase()

        val verdict = QueensDraftAnalyzer.analyze(QueensDraft(worst.size, worst.regions), spentAfterTen)

        assertEquals(Verdict.Undetermined, verdict)
    }

    @Test
    fun `an unlimited budget keeps the golden boards' pre-change solutions`() {
        val solver = QueensSolver()
        golden().forEach { (name, puzzle, solve) ->
            val expected = listOf(solve)
            assertEquals(name, expected, solver.solutionsUpTo(puzzle, limit = 2).map { s -> s.map { it.idx } })
            val bounded = QueensSearch.bounded(puzzle, limit = 2, budget = Budget.Unlimited)
            assertEquals(name, expected, (bounded as QueensSearchOutcome.Done).solutions.map { s -> s.map { it.idx } })
            assertEquals(name, solve, solver.solve(puzzle)!!.map { it.idx })
        }
    }

    private fun worstCase(): QueensPuzzle {
        val o = json("queens-editor-worst-12.json")
        return QueensPuzzle(
            size = o["size"]!!.jsonPrimitive.content.toInt(),
            regions = o["regions"]!!.jsonArray.map { it.jsonPrimitive.content.toInt() },
            queensPerUnit = o["queensPerUnit"]!!.jsonPrimitive.content.toInt(),
        )
    }

    private fun golden(): List<Triple<String, QueensPuzzle, List<Int>>> =
        json("queens-solver-golden.json")["boards"]!!.jsonArray.map { element ->
            val o = element.jsonObject
            fun ints(key: String) = o[key]!!.jsonArray.map { it.jsonPrimitive.content.toInt() }
            Triple(
                o["name"]!!.jsonPrimitive.content,
                QueensPuzzle(
                    size = o["size"]!!.jsonPrimitive.content.toInt(),
                    regions = ints("regions"),
                    queensPerUnit = o["queensPerUnit"]!!.jsonPrimitive.content.toInt(),
                ),
                ints("solve"),
            )
        }

    private fun json(name: String): JsonObject = Json.parseToJsonElement(
        QueensSearchBudgetTest::class.java.getResourceAsStream("/fixtures/$name")!!.readBytes().decodeToString(),
    ).jsonObject

    private companion object {
        const val BUDGET_MS = 1_500L
        const val SLACK_MS = 100L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
