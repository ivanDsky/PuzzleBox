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
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ticket 16 (spec A12/A13): Queens' lessons are 4×4 boards with one answer each, the
 * real controller solves each one from its intended taps (× then queen on every answer
 * square), no tap on the way ever shows a rule break, and a wrong queen is named by the
 * rule its lesson teaches. Lesson 1 is guided: it shows its answer and never a rule.
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

    /** [step] as the sandbox builds it: adopted, then guided when the step is guided. */
    private fun lessonFor(step: TutorialStep) = adopted(step).also { if (step.guided) it.guide() }

    /** One tap on [idx], through the board's own tap cycle. */
    private fun QueensPlayController.tap(idx: Int) = board!!.onTap(idx)

    /** The board model the screen draws right now (guide included). */
    private val QueensPlayController.model: QueensBoardModel
        get() = board!!.let { QueensBoardModel.of(it.session, it.hint, it.hintPointed, it.guide) }

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
            assertEquals(QueensPuzzleRef(PuzzleKey.Daily(LocalDate.of(2026, 1, 1), puzzleNumber = null)), step.ref)
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
            val s = lessonFor(step)

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
                // A guided lesson's faint queens leave each square as its queen lands.
                val expectedGhosts = if (step.guided) order.drop(placed + 1).toSet() else emptySet()
                assertEquals(at, expectedGhosts, s.model.ghosts)
            }
            assertTrue(step.instruction, s.isComplete)
        }
    }

    private fun assertClean(s: QueensPlayController, at: String) {
        val session = s.board!!.session
        assertFalse(at, QueensRules.evaluate(session.puzzle, session.queens.toSet()) is QueensRulesVerdict.Invalid)
        assertTrue(at, QueensBoardModel.of(session, s.board!!.hint, s.board!!.hintPointed).violations.isEmpty())
        assertNoRuleShown(s, at)
    }

    /** What the child sees: no rule stripes, no rule line, no caption at all. */
    private fun assertNoRuleShown(s: QueensPlayController, at: String) {
        assertTrue(at, s.model.violations.isEmpty())
        assertNull(at, s.violation)
        assertNull(at, s.rejection)
        assertNull(at, playCaption(s, s))
    }

    @Test
    fun `only the first lesson is guided`() {
        assertEquals(listOf(true, false, false), steps.map { it.guided })
    }

    @Test
    fun `the guided lesson shows its answer squares until each queen lands`() {
        val s = sandboxLesson(0)
        val answer = puzzleOf(steps[0]).solution!!.map { it.idx }.toSet()
        assertEquals(answer, s.board!!.guide)
        assertEquals(answer, s.model.ghosts)

        s.tap(2) // ×: the square still shows its faint queen
        assertEquals(answer, s.model.ghosts)
        s.tap(2) // the queen lands
        assertEquals(answer - 2, s.model.ghosts)
    }

    @Test
    fun `a guided lesson never shows a rule break, whatever the child taps`() {
        val s = sandboxLesson(0)
        var brokeARule = false
        // Every square through its whole cycle (×, queen, empty), in reading order.
        repeat(3) { round ->
            for (idx in 0 until 16) {
                s.tap(idx)
                val session = s.board!!.session
                brokeARule = brokeARule ||
                    QueensRules.evaluate(session.puzzle, session.queens.toSet()) is QueensRulesVerdict.Invalid
                assertNoRuleShown(s, "round ${round + 1}, square $idx")
            }
        }
        assertTrue("the taps did clash, so the guide is what kept the board calm", brokeARule)
    }

    @Test
    fun `the later lessons show no answer`() {
        for (lesson in 1..2) {
            val s = sandboxLesson(lesson)
            assertNull(s.board!!.guide)
            assertTrue(s.model.ghosts.isEmpty())
        }
    }

    @Test
    fun `a daily board is never guided`() {
        val daily = controller()
        assertEquals(Adopt.Adopted, daily.adoptToday(QueensPlayFixtures.envelope(), QueensPlayFixtures.ref()))
        assertNull(daily.board!!.guide)
        assertTrue(daily.model.ghosts.isEmpty())
    }

    @Test
    fun `guiding needs an adopted board`() {
        assertThrows(IllegalStateException::class.java) { controller().guide() }
    }

    /** Lesson [index]'s controller, built by the real sandbox at that lesson. */
    private fun sandboxLesson(index: Int): QueensPlayController {
        val session = TutorialSession(steps)
        repeat(index) { session.advance(); session.next() }
        val lesson = session.state.value as TutorialState.Playing
        assertEquals(index, lesson.index)
        return TutorialSandbox(QueensPlayUi(), session).controllerFor(lesson, scope) as QueensPlayController
    }

    /** Through the sandbox, as the host builds them: these lessons teach rules, so they show the break. */
    @Test
    fun `a wrong queen is named by the rule its lesson teaches`() {
        fun rulesBroken(lesson: Int, vararg queens: Int): Set<QueensRuleId> =
            sandboxLesson(lesson).run {
                // Auto-cross has already crossed a square that clashes, so one tap may do.
                queens.forEach { idx -> while (Cell(idx) !in board!!.session.queens) tap(idx) }
                assertFalse(isComplete)
                assertNotNull(violation)
                assertTrue(model.violations.isNotEmpty())
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
            assertEquals(lesson.step.guided, c.board!!.guide != null)
            lessonTaps.forEach { c.tap(it) }
            // The win marks the lesson solved; the host's success beat then moves on.
            assertTrue((session.state.value as TutorialState.Playing).solved)
            session.next()
        }
        assertEquals(TutorialState.Ready, session.state.value)
    }
}
