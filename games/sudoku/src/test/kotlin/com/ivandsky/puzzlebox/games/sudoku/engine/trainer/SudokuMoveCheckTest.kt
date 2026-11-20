package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuCandidates
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SudokuMoveCheckTest {

    private val walks = SudokuTrainerFixtures.all.mapValues { (_, puzzle) -> SudokuTechniqueWalker.walk(puzzle) }

    @Test
    fun `every walk step's own move is Right for its own id`() {
        var checked = 0
        for ((name, walk) in walks) {
            val puzzle = SudokuTrainerFixtures.all.getValue(name)
            for (w in walk) {
                val id = w.technique ?: continue
                val moves = when (val step = w.step) {
                    is SudokuStep.Single -> listOf(SudokuTrainerMove.Place(step.place.cell, step.place.digit))
                    is SudokuStep.Elimination -> step.struck.flatMap { (cell, mask) ->
                        (1..puzzle.geometry.size).filter { mask and (1 shl (it - 1)) != 0 }.map { SudokuTrainerMove.RemoveNote(cell, it) }
                    }
                    else -> error("$step")
                }
                moves.forEach {
                    assertEquals("$name ${id.value} $it", TrainerVerdict.Right, SudokuMoveCheck.classify(puzzle, w.boardBefore, w.struckBefore, id, it))
                    checked++
                }
            }
        }
        assertTrue(checked > 0)
    }

    @Test
    fun `a fitting placement the target single does not prove is AnotherWay`() {
        val anotherWay = HashSet<String>()
        for ((name, walk) in walks) {
            val puzzle = SudokuTrainerFixtures.all.getValue(name)
            for (w in walk.filter { it.step is SudokuStep.Single }) {
                for (idx in w.boardBefore.cells.indices.filter { w.boardBefore.digitAt(it) == 0 }) {
                    val move = SudokuTrainerMove.Place(idx, puzzle.solution[idx])
                    val verdict = SudokuMoveCheck.classify(puzzle, w.boardBefore, w.struckBefore, w.technique!!, move)
                    if (verdict == TrainerVerdict.AnotherWay) anotherWay += w.technique.value
                    // The step's own cell is proved; a placement elsewhere is not the step, so Right needs the rung to prove it too.
                    if (idx == (w.step as SudokuStep.Single).place.cell) assertEquals(TrainerVerdict.Right, verdict)
                }
            }
        }
        assertTrue("a single rung scored AnotherWay: $anotherWay", anotherWay.isNotEmpty())
    }

    @Test
    fun `a note removal the target elimination does not prove is AnotherWay, for each elimination id`() {
        val anotherWay = HashSet<String>()
        for ((name, walk) in walks) {
            val puzzle = SudokuTrainerFixtures.all.getValue(name)
            for (w in walk.filter { it.step is SudokuStep.Elimination }) {
                for ((idx, digit) in shownNotes(puzzle, w.boardBefore, w.struckBefore).filter { (i, d) -> d != puzzle.solution[i] }) {
                    val verdict = SudokuMoveCheck.classify(puzzle, w.boardBefore, w.struckBefore, w.technique!!, SudokuTrainerMove.RemoveNote(idx, digit))
                    if (verdict == TrainerVerdict.AnotherWay) anotherWay += w.technique.value
                    assertTrue("${w.technique} Wrong for a non-solution digit", verdict != TrainerVerdict.Wrong)
                }
            }
        }
        assertEquals(setOf("pointing", "claiming", "naked-pair", "hidden-pair"), anotherWay)
    }

    @Test
    fun `a move of the other kind is never Right, and AnotherWay when it fits`() {
        for ((name, walk) in walks) {
            val puzzle = SudokuTrainerFixtures.all.getValue(name)
            for (w in walk) {
                val id = w.technique ?: continue
                val empty = w.boardBefore.cells.indices.filter { w.boardBefore.digitAt(it) == 0 }
                val other: List<SudokuTrainerMove> = if (w.step is SudokuStep.Single) {
                    shownNotes(puzzle, w.boardBefore, w.struckBefore).filter { (i, d) -> d != puzzle.solution[i] }
                        .map { (i, d) -> SudokuTrainerMove.RemoveNote(i, d) }
                } else {
                    empty.map { SudokuTrainerMove.Place(it, puzzle.solution[it]) }
                }
                other.forEach {
                    assertEquals("$name ${id.value} $it", TrainerVerdict.AnotherWay, SudokuMoveCheck.classify(puzzle, w.boardBefore, w.struckBefore, id, it))
                }
            }
        }
    }

    @Test
    fun `a wrong digit placed and the solution digit removed are Wrong for every id`() {
        for ((name, walk) in walks) {
            val puzzle = SudokuTrainerFixtures.all.getValue(name)
            val w = walk.first()
            val idx = w.boardBefore.cells.indices.first { w.boardBefore.digitAt(it) == 0 }
            val wrong = puzzle.solution[idx] % puzzle.geometry.size + 1
            for (id in SudokuTechniqueIds.all) {
                assertEquals("$name $id", TrainerVerdict.Wrong, SudokuMoveCheck.classify(puzzle, w.boardBefore, w.struckBefore, id, SudokuTrainerMove.Place(idx, wrong)))
                assertEquals("$name $id", TrainerVerdict.Wrong, SudokuMoveCheck.classify(puzzle, w.boardBefore, w.struckBefore, id, SudokuTrainerMove.RemoveNote(idx, puzzle.solution[idx])))
            }
        }
    }

    @Test
    fun `a right move comes with the step that proves it, whichever instance the player found`() {
        var otherInstances = 0
        for ((name, walk) in walks) {
            val puzzle = SudokuTrainerFixtures.all.getValue(name)
            for (w in walk.filter { it.step is SudokuStep.Single }) {
                val own = (w.step as SudokuStep.Single).place
                for (idx in w.boardBefore.cells.indices.filter { w.boardBefore.digitAt(it) == 0 }) {
                    val move = SudokuTrainerMove.Place(idx, puzzle.solution[idx])
                    val judged = SudokuMoveCheck.judge(puzzle, w.boardBefore, w.struckBefore, w.technique!!, move)
                    if (judged.verdict != TrainerVerdict.Right) {
                        assertEquals("$name $move", null, judged.proof)
                        continue
                    }
                    val proof = (judged.proof as SudokuStep.Single).place
                    assertEquals("$name $move", idx to puzzle.solution[idx], proof.cell to proof.digit)
                    // The cut's own cell is proved by the cut's own technique.
                    if (idx == own.cell) assertEquals("$name $move", own.technique, proof.technique)
                    else otherInstances++
                }
            }
        }
        assertTrue("no walk board holds a second single of its technique", otherInstances > 0)
    }

    @Test
    fun `a right note removal comes with the elimination that strikes it`() {
        var checked = 0
        for ((name, walk) in walks) {
            val puzzle = SudokuTrainerFixtures.all.getValue(name)
            for (w in walk.filter { it.step is SudokuStep.Elimination }) {
                for ((cell, digit) in shownNotes(puzzle, w.boardBefore, w.struckBefore).filter { (cell, digit) -> digit != puzzle.solution[cell] }) {
                    val judged = SudokuMoveCheck.judge(puzzle, w.boardBefore, w.struckBefore, w.technique!!, SudokuTrainerMove.RemoveNote(cell, digit))
                    if (judged.verdict != TrainerVerdict.Right) {
                        assertEquals("$name $cell $digit", null, judged.proof)
                        continue
                    }
                    val proof = judged.proof as SudokuStep.Elimination
                    assertEquals("$name $cell $digit", (w.step as SudokuStep.Elimination).technique, proof.technique)
                    assertTrue("$name $cell $digit", (proof.struck[cell] ?: 0) and (1 shl (digit - 1)) != 0)
                    checked++
                }
            }
        }
        assertTrue(checked > 0)
    }

    @Test
    fun `bad moves and ids are refused`() {
        val puzzle = SudokuTrainerFixtures.dailies.getValue("li:1")
        val w = walks.getValue("li:1").first { it.technique != null }
        val id = w.technique!!
        val board = w.boardBefore
        val given = board.cells.indices.first { puzzle.givens[it] != 0 }
        val open = board.cells.indices.first { board.digitAt(it) == 0 }
        val filledBoard = walks.getValue("li:1").last().boardBefore
        val notACandidate = (1..puzzle.geometry.size).first { it !in SudokuCandidates.of(puzzle, board)[open].let { m -> (1..6).filter { d -> m and (1 shl (d - 1)) != 0 } } }

        fun refused(what: String, technique: TechniqueId = id, b: com.ivandsky.puzzlebox.games.sudoku.SudokuBoard = board, move: SudokuTrainerMove) {
            try {
                SudokuMoveCheck.classify(puzzle, b, emptyMap(), technique, move)
                fail("$what must be refused")
            } catch (expected: IllegalArgumentException) {
                // refused
            }
        }
        refused("unknown id", TechniqueId("no-such-technique"), move = SudokuTrainerMove.Place(open, 1))
        refused("place on a given", move = SudokuTrainerMove.Place(given, puzzle.solution[given]))
        refused("place on a filled cell", b = filledBoard, move = SudokuTrainerMove.Place(open, puzzle.solution[open]))
        refused("remove on a filled cell", b = filledBoard, move = SudokuTrainerMove.RemoveNote(open, 1))
        refused("remove of a digit that is no candidate", move = SudokuTrainerMove.RemoveNote(open, notACandidate))
        refused("off the board", move = SudokuTrainerMove.Place(puzzle.geometry.cellCount, 1))
        refused("digit off range", move = SudokuTrainerMove.Place(open, 0))
    }

    @Test
    fun `a struck candidate is no longer shown, so removing it is refused`() {
        val puzzle = SudokuTrainerFixtures.eliminating.getValue("rnd:1964")
        val w = walks.getValue("rnd:1964").first { it.step is SudokuStep.Elimination }
        val (cell, mask) = (w.step as SudokuStep.Elimination).struck.entries.first()
        val digit = Integer.numberOfTrailingZeros(mask) + 1
        val id = w.technique!!
        assertEquals(TrainerVerdict.Right, SudokuMoveCheck.classify(puzzle, w.boardBefore, w.struckBefore, id, SudokuTrainerMove.RemoveNote(cell, digit)))
        try {
            SudokuMoveCheck.classify(puzzle, w.boardBefore, mapOf(cell to mask), id, SudokuTrainerMove.RemoveNote(cell, digit))
            fail("a struck candidate is not shown")
        } catch (expected: IllegalArgumentException) {
            // refused
        }
    }

    /** Every (cell, digit) the snippet board shows as a note: the engine's candidates minus the strikes. */
    private fun shownNotes(puzzle: SudokuPuzzle, board: com.ivandsky.puzzlebox.games.sudoku.SudokuBoard, struck: Map<Int, Int>): List<Pair<Int, Int>> {
        val candidates = SudokuCandidates.of(puzzle, board)
        for ((cell, mask) in struck) candidates[cell] = candidates[cell] and mask.inv()
        return candidates.indices.flatMap { cell -> (1..puzzle.geometry.size).filter { candidates[cell] and (1 shl (it - 1)) != 0 }.map { cell to it } }
    }
}
