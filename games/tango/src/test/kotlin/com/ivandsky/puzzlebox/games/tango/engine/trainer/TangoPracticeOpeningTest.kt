package com.ivandsky.puzzlebox.games.tango.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoTestSupport
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The step a technique intro demonstrates is the one a focus board opens on (spec §2.7), on the real dailies. */
class TangoPracticeOpeningTest {

    private val dailies: List<Pair<String, TangoPuzzle>> =
        listOf(1, 301, 758).map { "byid-$it" to TangoTestSupport.dailyOf("linkedin-tango-byid-$it.json") }

    @Test
    fun `the opening board is the board a focus drill starts on, and exists exactly when that does`() {
        var found = 0
        for ((name, puzzle) in dailies) {
            val solution = TangoUniqueness.solve(puzzle)!!
            for ((rung, _) in TangoLadder.rungs) {
                val id = rung.trainerId
                val start = TangoPractice.start(puzzle, solution, id, BoardKind.Focus)
                val opening = TangoPractice.opening(puzzle, solution, id, BoardKind.Focus)
                assertEquals("$name ${id.value}", start, opening?.board)
                if (opening != null) found++
            }
        }
        assertTrue("the dailies open some focus board", found > 0)
    }

    @Test
    fun `the opening step is a whole deduction of the trained rung that settles open squares with solution values`() {
        for ((name, puzzle) in dailies) {
            val solution = TangoUniqueness.solve(puzzle)!!
            for ((rung, _) in TangoLadder.rungs) {
                val opening = TangoPractice.opening(puzzle, solution, rung.trainerId, BoardKind.Focus) ?: continue
                val label = "$name ${rung.trainerId.value}"
                assertEquals(label, rung, opening.step.rung)
                for (square in opening.step.squares) {
                    assertNull(label, opening.board[square.idx])
                    assertEquals(label, solution[square.idx], square.value)
                    val shown = square.shown as TangoStep.Deduction
                    assertEquals(label, rung, shown.rung)
                    assertEquals(label, square.idx, shown.idx)
                }
            }
        }
    }

    @Test
    fun `every square of the opening step is one the trained rule proves on the opening board`() {
        for ((name, puzzle) in dailies) {
            val solution = TangoUniqueness.solve(puzzle)!!
            for ((rung, rule) in TangoLadder.rungs) {
                val opening = TangoPractice.opening(puzzle, solution, rung.trainerId, BoardKind.Focus) ?: continue
                val forced = rule.forced(puzzle, opening.board).map { it.idx }.toSet()
                assertTrue("$name ${rung.trainerId.value}", opening.step.squares.all { it.idx in forced })
            }
        }
    }

    @Test
    fun `a technique that is no Tango technique has no opening`() {
        val (_, puzzle) = dailies.first()
        val solution = TangoUniqueness.solve(puzzle)!!
        assertNull(TangoPractice.opening(puzzle, solution, TechniqueId("hidden-single"), BoardKind.Focus))
    }
}
