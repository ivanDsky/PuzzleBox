package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.MarkKind
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRuleId
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRules
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensRulesVerdict
import com.ivandsky.puzzlebox.games.queens.feed.QueensUniquenessGate
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.TutorialSandbox
import com.ivandsky.puzzlebox.gameui.TutorialSession
import com.ivandsky.puzzlebox.gameui.TutorialState
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.gameui.playCaption
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ticket 16 (spec A12/A13): Queens' lessons are 4×4 boards with one answer each, the
 * real controller solves each one from its intended taps (× then queen on every answer
 * square), no tap on the way ever shows a rule break, and a wrong queen is named by the
 * rule its lesson teaches.
 */
class QueensTutorialTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val steps = QueensTutorial.steps

    private fun puzzleOf(step: TutorialStep): QueensPuzzle =
        (QueensCodec.decode(step.puzzle.payload) as DecodeResult.Ok).value

    /** The answer squares in the order a child reasons them out. */
    private val answerOrder = listOf(
        listOf(2, 4, 11, 13),
        listOf(14, 8, 7, 1),
        listOf(2, 4, 11, 13),
    )

    /** Each lesson's intended taps: every answer square twice, × then queen. */
    private val taps: List<List<Int>> = answerOrder.map { order -> order.flatMap { listOf(it, it) } }

    private fun controller(won: MutableList<CompletionRecord> = mutableListOf()) = QueensPlayController(
        env = PlayEnvironment(scope, onSessionChanged = {}, onWon = { won += it }),
    )

    private fun adopted(step: TutorialStep, won: MutableList<CompletionRecord> = mutableListOf()) =
        controller(won).also { assertEquals(Adopt.Adopted, it.adoptToday(step.puzzle, step.ref)) }

    /** One tap on [idx], through the board's own tap cycle. */
    private fun QueensPlayController.tap(idx: Int) = board!!.onTap(idx)

    /** Answers to [puzzle] under the row, column and color rules only (the touch rule off). */
    private fun answersWithoutTouch(puzzle: QueensPuzzle): Int {
        val n = puzzle.size
        fun place(row: Int, cols: Set<Int>, colors: Set<Int>): Int = if (row == n) {
            1
        } else {
            (0 until n).filter { it !in cols && puzzle.regionAt(row * n + it) !in colors }
                .sumOf { place(row + 1, cols + it, colors + puzzle.regionAt(row * n + it)) }
        }
        return place(0, emptySet(), emptySet())
    }

    @Test
    fun `the play registration ships three lessons`() {
        assertSame(steps, QueensPlayUi().tutorial)
        assertEquals(3, steps.size)
        assertEquals(steps.size, taps.size)
    }

    @Test
    fun `every lesson is a legal 4x4 board that passes the uniqueness gate`() {
        for ((step, order) in steps.zip(answerOrder)) {
            val puzzle = puzzleOf(step)

            assertEquals(QueensDefinition.id, step.puzzle.gameId)
            assertTrue(QueensDefinition.decode(step.puzzle.payload) is DecodeResult.Ok)
            assertEquals(4, puzzle.size)
            assertNull(step.instruction, QueensUniquenessGate.check(puzzle))
            assertEquals(step.instruction, puzzle.solution!!.toSet(), order.map(::Cell).toSet())
        }
    }

    @Test
    fun `every lesson plays as a custom board, never a daily`() {
        for (step in steps) {
            assertEquals(QueensPuzzleRef(LocalDate.of(2026, 1, 1), puzzleNumber = null), step.ref)
        }
    }

    @Test
    fun `lessons one and two need no touch rule, and lesson three needs it`() {
        assertEquals(1, answersWithoutTouch(puzzleOf(steps[0])))
        assertEquals(1, answersWithoutTouch(puzzleOf(steps[1])))
        assertEquals(6, answersWithoutTouch(puzzleOf(steps[2])))
    }

    @Test
    fun `each lesson is solved by its intended taps through the real controller`() {
        for ((step, lessonTaps) in steps.zip(taps)) {
            val won = mutableListOf<CompletionRecord>()
            val s = adopted(step, won)

            lessonTaps.dropLast(1).forEach { s.tap(it) }
            assertFalse(step.instruction, s.isComplete)
            s.tap(lessonTaps.last())

            assertTrue(step.instruction, s.isComplete)
            // The win is what moves the tutorial on: one record, under the lesson's own ref.
            assertEquals(step.ref, won.single().puzzleRef)
        }
    }

    @Test
    fun `no intended tap ever breaks a rule or shows a caption, not even for a moment`() {
        for ((step, order) in steps.zip(answerOrder)) {
            val s = adopted(step)

            for ((placed, idx) in order.withIndex()) {
                val cell = Cell(idx)
                val at = "${step.instruction} (queen ${placed + 1})"
                // Auto-cross never covers an answer square, so its first tap is the ×.
                assertNull(at, s.board!!.session.markAt(cell))

                s.tap(idx)
                assertEquals(at, MarkKind.MANUAL, s.board!!.session.markAt(cell)?.kind)
                assertClean(s, "$at, ×")

                s.tap(idx)
                assertTrue(at, cell in s.board!!.session.queens)
                assertEquals(at, placed + 1, s.board!!.session.queens.size)
                assertClean(s, "$at, queen")
            }
            assertTrue(step.instruction, s.isComplete)
        }
    }

    private fun assertClean(s: QueensPlayController, at: String) {
        val session = s.board!!.session
        assertFalse(at, QueensRules.evaluate(session.puzzle, session.queens.toSet()) is QueensRulesVerdict.Invalid)
        assertTrue(at, QueensBoardModel.of(session, s.board!!.hint, s.board!!.hintPointed).violations.isEmpty())
        assertNull(at, s.violation)
        assertNull(at, s.rejection)
        assertNull(at, playCaption(s, s))
    }

    @Test
    fun `a wrong queen is named by the rule its lesson teaches`() {
        fun rulesBroken(lesson: Int, vararg queens: Int): Set<QueensRuleId> =
            adopted(steps[lesson]).run {
                // Auto-cross has already crossed a square that clashes, so one tap may do.
                queens.forEach { idx -> while (Cell(idx) !in board!!.session.queens) tap(idx) }
                assertFalse(isComplete)
                assertEquals(RULE_CONFLICT, violation)
                val verdict = QueensRules.evaluate(board!!.session.puzzle, board!!.session.queens.toSet())
                (verdict as QueensRulesVerdict.Invalid).violations.map { it.rule }.toSet()
            }

        // Lesson 2: two queens on the same color, apart and on different lines.
        assertEquals(setOf(QueensRuleId.RegionExclusivity), rulesBroken(lesson = 1, 0, 6))
        // Lesson 3: the top queen and a square touching it only by the corner.
        assertEquals(setOf(QueensRuleId.NoTouchAdjacency), rulesBroken(lesson = 2, 2, 7))
    }

    @Test
    fun `the play registration's tutorial plays through the sandbox to ready`() {
        val ui = QueensPlayUi()
        val session = TutorialSession(ui.tutorial)
        val sandbox = TutorialSandbox(ui, session)
        for ((index, lessonTaps) in taps.withIndex()) {
            val lesson = session.state.value as TutorialState.Playing
            assertEquals(index, lesson.index)
            val c = sandbox.controllerFor(lesson, scope) as QueensPlayController
            lessonTaps.forEach { c.tap(it) }
        }
        assertEquals(TutorialState.Ready, session.state.value)
    }

    private companion object {
        const val RULE_CONFLICT = "Queens can't touch or share a row, column, or color."
    }
}
