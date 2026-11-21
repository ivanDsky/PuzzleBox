package com.ivandsky.puzzlebox.games.tango.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoTestSupport
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where a focus or a review board opens and when it is done (spec §2.2), on the real dailies. */
class TangoPracticeKindsTest {

    private val order = TangoLadder.rungs.map { it.first.trainerId }

    private class Case(val name: String, val puzzle: TangoPuzzle, val solution: List<TangoCell>, val technique: TechniqueId)

    private val cases: List<Case> = dailies.flatMap { (name, puzzle) ->
        val solution = TangoUniqueness.solve(puzzle)!!
        order.map { Case(name, puzzle, solution, it) }
    }

    private fun lowestRung(puzzle: TangoPuzzle, grid: List<TangoCell?>): TechniqueId? =
        TangoLadder.rungs.firstNotNullOfOrNull { (rung, rule) -> rung.trainerId.takeIf { rule.forced(puzzle, grid).isNotEmpty() } }

    /**
     * The opening the rule asks for, worked out from the walk by cutting it into runs of points whose rung is [inRun]
     * (a point with no rung is in no run: it ends a board, so it breaks the run), taking the run with the most trained
     * points (the earliest on a tie) and its first trained point.
     */
    private fun expectedStart(case: Case, inRun: (TechniqueId) -> Boolean): List<TangoCell?>? {
        val runs = mutableListOf<MutableList<TangoWalkPoint>>()
        var previousIn = false
        for (point in TangoPractice.walk(case.puzzle, case.solution)) {
            val inside = point.technique?.let(inRun) ?: false
            if (inside) {
                if (!previousIn) runs.add(mutableListOf())
                runs.last() += point
            }
            previousIn = inside
        }
        val withTrained = runs.map { run -> run.filter { it.technique == case.technique } }.filter { it.isNotEmpty() }
        return withTrained.fold(null as List<TangoWalkPoint>?) { best, run -> if (best == null || run.size > best.size) run else best }
            ?.first()?.board
    }

    private fun focusStart(case: Case) = TangoPractice.start(case.puzzle, case.solution, case.technique, BoardKind.Focus)

    private fun reviewStart(case: Case) = TangoPractice.start(case.puzzle, case.solution, case.technique, BoardKind.Review)

    private fun learned(case: Case): (TechniqueId) -> Boolean = { id -> order.indexOf(id) <= order.indexOf(case.technique) }

    @Test
    fun `a focus board opens at the start of the longest run of the technique, the earliest on a tie`() {
        var notFirst = 0
        for (case in cases) {
            val expected = expectedStart(case) { it == case.technique }
            assertEquals("${case.name} ${case.technique.value}", expected, focusStart(case))
            val firstTrained = TangoPractice.walk(case.puzzle, case.solution).firstOrNull { it.technique == case.technique }?.board
            if (expected != null && expected != firstTrained) notFirst++
        }
        assertTrue("some daily's longest run is not its first run", notFirst > 0)
    }

    @Test
    fun `a review board opens at the first trained point of the run of learned points with the most trained points`() {
        var differs = 0
        for (case in cases) {
            val expected = expectedStart(case, learned(case))
            assertEquals("${case.name} ${case.technique.value}", expected, reviewStart(case))
            if (expected != focusStart(case)) differs++
        }
        assertTrue("a review board opens elsewhere than the focus board", differs > 0)
    }

    /** The boards a player who follows the easiest rung walks through from [start] until the board is done. */
    private fun playOut(case: Case, start: List<TangoCell?>, kind: BoardKind): List<List<TangoCell?>> {
        var grid = start
        var last: Int? = null
        val boards = mutableListOf(grid)
        while (!TangoPractice.isDone(case.puzzle, grid, case.technique, kind)) {
            val step = checkNotNull(TangoPractice.show(case.puzzle, case.solution, grid, last)) { "a step on $grid" }
            grid = grid.toMutableList().also { cells -> step.squares.forEach { cells[it.idx] = it.value } }
            last = step.squares.last().idx
            boards += grid
        }
        return boards
    }

    @Test
    fun `a focus board asks only the trained technique and is done as soon as another rung is easiest, solved or not`() {
        var doneUnsolved = 0
        for (case in cases) {
            val start = focusStart(case) ?: continue
            val boards = playOut(case, start, BoardKind.Focus)
            boards.dropLast(1).forEach { assertEquals("${case.name} ${case.technique.value}", case.technique, lowestRung(case.puzzle, it)) }
            val done = boards.last()
            if (done.any { it == null }) {
                doneUnsolved++
                assertNotEquals("${case.name} ${case.technique.value}", case.technique, lowestRung(case.puzzle, done))
            }
        }
        assertTrue("a focus board ends on another rung with squares left", doneUnsolved > 0)
    }

    @Test
    fun `a review board asks learned techniques and is done on a later one, solved or not`() {
        var learnedOther = 0
        var doneUnsolved = 0
        for (case in cases) {
            val start = reviewStart(case) ?: continue
            val isLearned = learned(case)
            val boards = playOut(case, start, BoardKind.Review)
            boards.dropLast(1).forEach { board ->
                val rung = lowestRung(case.puzzle, board)
                assertTrue("${case.name} ${case.technique.value}", rung != null && isLearned(rung))
                if (rung != null && rung != case.technique) learnedOther++
            }
            val done = boards.last()
            if (done.any { it == null }) {
                doneUnsolved++
                assertFalse("${case.name} ${case.technique.value}", lowestRung(case.puzzle, done)?.let(isLearned) ?: false)
            }
        }
        assertTrue("a review board asks an earlier technique", learnedOther > 0)
        assertTrue("a review board ends on a later rung with squares left", doneUnsolved > 0)
    }

    @Test
    fun `a solved board is done for either kind`() {
        for (case in cases) {
            BoardKind.entries.forEach { kind ->
                assertTrue(TangoPractice.isDone(case.puzzle, case.solution, case.technique, kind))
            }
        }
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
