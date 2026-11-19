package com.ivandsky.puzzlebox.games.tango.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoTestSupport
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Boards come from the walks. byid-1 (6×6, solution SMSMSM MSMMSS SSMSMM SMSMSM MSMMMS SMSMSM,
 * givens 0, 2, 12, 14, 16, 26, 28) opens with an equal pair at 4 while sign count, count squeeze
 * and line options prove 4 as well; byid-758 proves 12 by a split pair at step 16 though the
 * stepper shows line options 16 there.
 */
class TangoMoveCheckTest {

    @Test
    fun `every walk step's own move is right for its own technique`() {
        for ((name, puzzle) in dailies) {
            val solution = TangoUniqueness.solve(puzzle)!!
            for (w in TangoTechniqueWalker.walk(puzzle, solution)) {
                val technique = w.technique ?: continue
                val d = w.step as TangoStep.Deduction
                assertEquals(
                    "$name $technique $d",
                    TrainerVerdict.Right,
                    TangoMoveCheck.classify(puzzle, solution, w.gridBefore, technique, TangoTrainerMove(d.idx, d.value)),
                )
            }
        }
    }

    @Test
    fun `a fitting move the technique does not prove is another way`() {
        val (puzzle, solution) = solved("byid-1")
        // Step 1 (full line 5): sign proves 3 and 10, not 1; full line proves 1, 3, 5, not 10; equal pair proves 24, 25, not 6.
        val afterOne = board("byid-1", 1, "full-line")
        assertEquals(TrainerVerdict.AnotherWay, check(puzzle, solution, afterOne, "sign", 1))
        assertEquals(TrainerVerdict.AnotherWay, check(puzzle, solution, afterOne, "full-line", 10))
        assertEquals(TrainerVerdict.AnotherWay, check(puzzle, solution, afterOne, "equal-pair", 6))
        assertEquals(TrainerVerdict.Right, check(puzzle, solution, afterOne, "sign", 10))
        assertEquals(TrainerVerdict.Right, check(puzzle, solution, afterOne, "full-line", 1))
    }

    @Test
    fun `right reads the technique's full proofs, not the step the stepper shows first`() {
        val (puzzle, solution) = solved("byid-1")
        val opening = board("byid-1", 0, "equal-pair")
        // The stepper shows 4; the same rung proves 10, 24 and 25 on that board too.
        listOf(4, 10, 24, 25).forEach { assertEquals("$it", TrainerVerdict.Right, check(puzzle, solution, opening, "equal-pair", it)) }
        // Sign count, count squeeze and line options prove 4 as well; sandwich does not.
        listOf("sign-count", "count-squeeze", "line-options").forEach {
            assertEquals(it, TrainerVerdict.Right, check(puzzle, solution, opening, it, 4))
        }
        assertEquals(TrainerVerdict.AnotherWay, check(puzzle, solution, opening, "sandwich", 4))
        // byid-758 step 16: the stepper shows line options 16, a split pair proves 12.
        val (puzzle758, solution758) = solved("byid-758")
        val stuck = board("byid-758", 16, "line-options")
        assertEquals(TrainerVerdict.Right, check(puzzle758, solution758, stuck, "split-pair", 12))
        assertEquals(TrainerVerdict.AnotherWay, check(puzzle758, solution758, stuck, "split-pair", 16))
    }

    @Test
    fun `the opposite value on a target cell is wrong`() {
        for ((name, puzzle) in dailies) {
            val solution = TangoUniqueness.solve(puzzle)!!
            for (w in TangoTechniqueWalker.walk(puzzle, solution)) {
                val technique = w.technique ?: continue
                val d = w.step as TangoStep.Deduction
                assertEquals(
                    "$name $technique $d",
                    TrainerVerdict.Wrong,
                    TangoMoveCheck.classify(puzzle, solution, w.gridBefore, technique, TangoTrainerMove(d.idx, d.value.other)),
                )
            }
        }
    }

    @Test
    fun `an unknown technique, a given, a filled or an off-board cell is refused`() {
        val (puzzle, solution) = solved("byid-1")
        val afterOne = board("byid-1", 1, "full-line")
        assertEquals(TangoCell.SUN, afterOne[4])
        val refused = listOf(
            { check(puzzle, solution, afterOne, "cross", 1) },
            { check(puzzle, solution, afterOne, "sign", 0) },
            { check(puzzle, solution, afterOne, "sign", 4) },
            { check(puzzle, solution, afterOne, "sign", -1) },
            { check(puzzle, solution, afterOne, "sign", puzzle.cellCount) },
        )
        refused.forEach { call -> assertThrows(IllegalArgumentException::class.java) { call() } }
    }

    /** Checks the solution's own value on [idx]. */
    private fun check(puzzle: TangoPuzzle, solution: List<TangoCell>, grid: List<TangoCell?>, technique: String, idx: Int): TrainerVerdict =
        TangoMoveCheck.classify(puzzle, solution, grid, TechniqueId(technique), TangoTrainerMove(idx, solution.getOrElse(idx) { TangoCell.SUN }))

    private fun solved(name: String): Pair<TangoPuzzle, List<TangoCell>> = dailies.getValue(name).let { it to TangoUniqueness.solve(it)!! }

    /** The grid before walk step [index] of [name], checked to be a step of [technique]. */
    private fun board(name: String, index: Int, technique: String): List<TangoCell?> {
        val (puzzle, solution) = solved(name)
        val step = TangoTechniqueWalker.walk(puzzle, solution)[index]
        assertEquals("${step.step}", TechniqueId(technique), step.technique)
        return step.gridBefore
    }

    private companion object {
        val dailies: Map<String, TangoPuzzle> by lazy {
            mapOf(
                "byid-1" to TangoTestSupport.dailyOf("linkedin-tango-byid-1.json"),
                "byid-301" to TangoTestSupport.dailyOf("linkedin-tango-byid-301.json"),
                "byid-758" to TangoTestSupport.dailyOf("linkedin-tango-byid-758.json"),
            )
        }
    }
}
