package com.ivandsky.puzzlebox.games.queens.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensTestSupport
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Boards come from the walks: real-916 (7×7, solution 6, 8, 17, 26, 30, 39, 42) opens with a
 * line-lock crossing 0, 7, 14, 21, 28 and then places 6 by singles; unique (8×8, solution 5, 10,
 * 16, 28, 39, 43, 49, 62) crosses 13 by a short look-ahead at step 1 and 56 by a long one at step 2.
 */
class QueensMoveCheckTest {

    @Test
    fun `every walk step's own move is right for its own technique`() {
        for ((name, puzzle) in dailies) {
            for (w in QueensTechniqueWalker.walk(puzzle)) {
                val technique = w.technique ?: continue
                val moves = when (val r = (w.step as QueensStep.Deduction).result) {
                    is QueensStep.Deduction.Result.Cross -> r.cells.map { QueensTrainerMove.Cross(it) }
                    is QueensStep.Deduction.Result.LastCell -> listOf(QueensTrainerMove.Place(r.cell))
                }
                moves.forEach { move ->
                    assertEquals("$name $technique $move", TrainerVerdict.Right, QueensMoveCheck.classify(puzzle, w.boardBefore, technique, move))
                }
            }
        }
    }

    @Test
    fun `a fitting move the technique does not prove is another way`() {
        val real = dailies.getValue("real-916")
        val empty = board(real, 0, "line-lock")
        assertEquals(TrainerVerdict.AnotherWay, check(real, empty, "line-lock", QueensTrainerMove.Place(Cell(6))))
        val crossed = board(real, 1, "singles")
        assertEquals(TrainerVerdict.Right, check(real, crossed, "singles", QueensTrainerMove.Place(Cell(6))))
        assertEquals(TrainerVerdict.AnotherWay, check(real, crossed, "singles", QueensTrainerMove.Cross(Cell(1))))
    }

    @Test
    fun `right reads the technique's own proofs, not the step the stepper shows first`() {
        // On the empty board the stepper shows a line-lock first, yet singles already proves 6.
        val real = dailies.getValue("real-916")
        val empty = board(real, 0, "line-lock")
        assertEquals(TrainerVerdict.Right, check(real, empty, "singles", QueensTrainerMove.Place(Cell(6))))
    }

    @Test
    fun `a look-ahead at the other depth is another way`() {
        val unique = dailies.getValue("unique")
        val short = board(unique, 1, "short-look-ahead")
        assertEquals(TrainerVerdict.Right, check(unique, short, "short-look-ahead", QueensTrainerMove.Cross(Cell(13))))
        assertEquals(TrainerVerdict.AnotherWay, check(unique, short, "long-look-ahead", QueensTrainerMove.Cross(Cell(13))))
        val long = board(unique, 2, "long-look-ahead")
        assertEquals(TrainerVerdict.Right, check(unique, long, "long-look-ahead", QueensTrainerMove.Cross(Cell(56))))
        assertEquals(TrainerVerdict.AnotherWay, check(unique, long, "short-look-ahead", QueensTrainerMove.Cross(Cell(56))))
    }

    @Test
    fun `a move that breaks the solution is wrong`() {
        val real = dailies.getValue("real-916")
        val crossed = board(real, 1, "singles")
        assertEquals(TrainerVerdict.Wrong, check(real, crossed, "singles", QueensTrainerMove.Place(Cell(1))))
        assertEquals(TrainerVerdict.Wrong, check(real, crossed, "singles", QueensTrainerMove.Cross(Cell(6))))
        val unique = dailies.getValue("unique")
        val long = board(unique, 2, "long-look-ahead")
        assertEquals(TrainerVerdict.Wrong, check(unique, long, "long-look-ahead", QueensTrainerMove.Place(Cell(56))))
        assertEquals(TrainerVerdict.Wrong, check(unique, long, "long-look-ahead", QueensTrainerMove.Cross(Cell(5))))
    }

    @Test
    fun `an unknown technique, a taken cell or a missing solution is refused`() {
        val real = dailies.getValue("real-916")
        val crossed = board(real, 1, "singles")
        val queened = QueensTechniqueWalker.walk(real)[2].boardBefore
        assertEquals(listOf(Cell(6)), queened.queens)
        val refused = listOf(
            { check(real, crossed, "attack", QueensTrainerMove.Place(Cell(6))) },
            { check(real, crossed, "singles", QueensTrainerMove.Cross(Cell(0))) },
            { check(real, queened, "singles", QueensTrainerMove.Place(Cell(6))) },
            { check(real, queened, "singles", QueensTrainerMove.Cross(Cell(5))) },
            { check(real, crossed, "singles", QueensTrainerMove.Place(Cell(real.cellCount))) },
            { check(real.copy(solution = null), crossed, "singles", QueensTrainerMove.Place(Cell(6))) },
        )
        refused.forEach { call -> assertThrows(IllegalArgumentException::class.java) { call() } }
    }

    private fun check(puzzle: QueensPuzzle, board: QueensBoard, technique: String, move: QueensTrainerMove): TrainerVerdict =
        QueensMoveCheck.classify(puzzle, board, TechniqueId(technique), move)

    /** The board before walk step [index], checked to be a step of [technique]. */
    private fun board(puzzle: QueensPuzzle, index: Int, technique: String): QueensBoard {
        val step = QueensTechniqueWalker.walk(puzzle)[index]
        assertEquals("${step.step}", TechniqueId(technique), step.technique)
        return step.boardBefore
    }

    private companion object {
        val dailies: Map<String, QueensPuzzle> by lazy {
            mapOf(
                "real-916" to QueensTestSupport.daily("queens-daily-real-916.json"),
                "unique" to QueensTestSupport.daily("queens-daily-unique.json"),
                "sample" to QueensTestSupport.daily("queens-daily-sample.json"),
            )
        }
    }
}
