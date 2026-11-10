package com.ivandsky.puzzlebox.games.queens.engine.hints.step

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensCandidates
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensDeduction
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.LongLookAheadRung
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.ShortLookAheadRung
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SC7 / A19: the stepper never hangs, on a stalled state and on one that needs a look-ahead rung. A hang guard, not a perf gate. */
class QueensStepperTimingTest {

    /** The 12×12 worst-12 editor board has no solution: Stepwise finds no proof at any rung and stalls. */
    private val worst12: QueensPuzzle = Json.parseToJsonElement(
        QueensStepperTimingTest::class.java.getResourceAsStream("/fixtures/queens-editor-worst-12.json")!!.readBytes().decodeToString(),
    ).jsonObject.let { o ->
        QueensPuzzle(
            size = o.getValue("size").jsonPrimitive.content.toInt(),
            regions = o.getValue("regions").jsonArray.map { it.jsonPrimitive.content.toInt() },
            queensPerUnit = o.getValue("queensPerUnit").jsonPrimitive.content.toInt(),
        )
    }

    /** Where Stepwise stalls on [worst12] from empty, as the board the player would hold. */
    private fun stalledBoard(): QueensBoard {
        val stall = generateSequence(QueensCandidates.start(worst12, emptySet())) { state ->
            when (val d = QueensLadder.Stepwise.next(worst12, state)) {
                null -> null
                is QueensDeduction.Place -> state.place(worst12, d.cell)
                is QueensDeduction.Exclude -> state.exclude(d.cells)
            }
        }.last()
        return QueensBoard(stall.queens.map(::Cell), stall.excluded.map(::Cell).toSet())
    }

    @Test
    fun `next on the hardest stalled state returns in under two seconds`() {
        val board = stalledBoard()
        val warm = QueensStepper.next(worst12, board, autoCross = true, lastMove = null, solution = null)
        assertEquals(QueensStep.Unavailable, warm)

        val start = System.nanoTime()
        QueensStepper.next(worst12, board, autoCross = true, lastMove = null, solution = null)
        val millis = (System.nanoTime() - start) / 1_000_000

        println("QueensStepperTimingTest worst-12 stalled next: $millis ms")
        assertTrue("next took $millis ms, bound is 2000 ms", millis < 2_000)
    }

    /**
     * Positive control: gen-n8-q1 with one manual cross on cell 2 and no queens: found by replaying the full stepper, its first
     * step is a look-ahead rung's. The look-ahead
     * path must run, produce a step, and stay under the bound, so an instantly empty chain search cannot pass.
     */
    @Test
    fun `next on a state that needs look-ahead returns a look-ahead step in under two seconds`() {
        val o = Json.parseToJsonElement(
            QueensStepperTimingTest::class.java.getResourceAsStream("/fixtures/queens-solver-golden.json")!!.readBytes().decodeToString(),
        ).jsonObject.getValue("boards").jsonArray.map { it.jsonObject }.single { it.getValue("name").jsonPrimitive.content == "gen-n8-q1" }
        val n8 = QueensPuzzle(
            size = o.getValue("size").jsonPrimitive.content.toInt(),
            regions = o.getValue("regions").jsonArray.map { it.jsonPrimitive.content.toInt() },
            queensPerUnit = o.getValue("queensPerUnit").jsonPrimitive.content.toInt(),
            solution = o.getValue("solve").jsonArray.map { Cell(it.jsonPrimitive.content.toInt()) },
        )
        val solution = n8.solution!!.toSet()
        val board = QueensBoard(emptyList(), setOf(Cell(2)))
        QueensStepper.next(n8, board, autoCross = true, lastMove = null, solution = solution)

        val start = System.nanoTime()
        val step = QueensStepper.next(n8, board, autoCross = true, lastMove = null, solution = solution)
        val millis = (System.nanoTime() - start) / 1_000_000

        println("QueensStepperTimingTest gen-n8-q1 look-ahead next: $millis ms")
        val rung = (step as QueensStep.Deduction).rung
        assertTrue("$step", rung is ShortLookAheadRung || rung is LongLookAheadRung)
        assertTrue("next took $millis ms, bound is 2000 ms", millis < 2_000)
    }
}
