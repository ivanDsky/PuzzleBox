package com.ivandsky.puzzlebox.games.queens.engine.hints.step

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensCandidates
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensDeduction
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SC7 / A19: the stepper never hangs, even where every look-ahead chain runs to its cap. A hang guard, not a perf gate. */
class QueensStepperTimingTest {

    /** The 12×12 worst-12 editor board has no solution: Stepwise runs every chain to its cap and stalls. */
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
}
