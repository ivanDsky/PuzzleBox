package com.ivandsky.puzzlebox.games.tango.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoTestSupport
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The practice board's engine on the real dailies: where a drill starts, how a move is judged, and what Show me plays. */
class TangoPracticeTest {

    private val techniques = TangoStepRung.entries.map { it.trainerId }

    /** The lowest rung with a deduction on [grid], read straight off the ladder's rules. */
    private fun lowestRung(puzzle: TangoPuzzle, grid: List<TangoCell?>): TangoStepRung? =
        TangoLadder.rungs.firstNotNullOfOrNull { (rung, rule) -> rung.takeIf { rule.forced(puzzle, grid).isNotEmpty() } }

    private fun openGrid(puzzle: TangoPuzzle): List<TangoCell?> = List(puzzle.cellCount) { puzzle.givens[it] }

    /** Every (technique, start board) the dailies give. */
    private fun starts(): List<Triple<String, TechniqueId, List<TangoCell?>>> = dailies.flatMap { (name, puzzle) ->
        val solution = TangoUniqueness.solve(puzzle)!!
        techniques.mapNotNull { technique ->
            TangoPractice.start(puzzle, solution, technique, BoardKind.Focus)?.let { Triple(name, technique, it) }
        }
    }

    @Test
    fun `a drill starts where its technique is the easiest rung, on a board of givens and solution values`() {
        val boards = starts()
        assertTrue("the dailies start drills", boards.size >= 5)
        for ((name, technique, grid) in boards) {
            val puzzle = dailies.getValue(name)
            val solution = TangoUniqueness.solve(puzzle)!!
            val label = "$name ${technique.value}"
            assertEquals(label, technique, lowestRung(puzzle, grid)?.trainerId)
            assertTrue(label, puzzle.givens.all { (idx, value) -> grid[idx] == value })
            assertTrue(label, grid.withIndex().all { (idx, value) -> value == null || value == solution[idx] })
            assertTrue("$label: a square is left to play", grid.any { it == null })
        }
    }

    @Test
    fun `a harder technique opens after easier steps were taken`() {
        for ((name, puzzle) in dailies) {
            val solution = TangoUniqueness.solve(puzzle)!!
            val open = openGrid(puzzle)
            val first = checkNotNull(lowestRung(puzzle, open)) { "$name opens with a deduction" }
            techniques.filter { it != first.trainerId }.forEach { technique ->
                BoardKind.entries.forEach { kind ->
                    val grid = TangoPractice.start(puzzle, solution, technique, kind) ?: return@forEach
                    assertTrue("$name ${technique.value} $kind: easier steps filled squares first", grid.count { it != null } > open.count { it != null })
                }
            }
        }
    }

    @Test
    fun `a technique the walk never reaches as the easiest rung starts no drill`() {
        val never = dailies.flatMap { (name, puzzle) ->
            val solution = TangoUniqueness.solve(puzzle)!!
            techniques.filter { TangoPractice.start(puzzle, solution, it, BoardKind.Focus) == null }.map { name to it }
        }
        assertTrue("some technique is never the easiest rung on a daily", never.isNotEmpty())
        assertTrue("the split pair is one of them on byid-1", "byid-1" to TechniqueId("split-pair") in never)
    }

    @Test
    fun `an id that is no Tango technique, and a grid that is no solution, start no drill`() {
        val puzzle = dailies.getValue("byid-1")
        val solution = TangoUniqueness.solve(puzzle)!!
        val sign = TechniqueId("sign")
        BoardKind.entries.forEach { kind ->
            assertNotNull(TangoPractice.start(puzzle, solution, sign, kind))

            assertNull(TangoPractice.start(puzzle, solution, TechniqueId("no-such-technique"), kind))
            val open = (0 until puzzle.cellCount).first { !puzzle.isGiven(it) }
            assertNull("breaks a rule", TangoPractice.start(puzzle, solution.toMutableList().also { it[open] = it[open].other }, sign, kind))
            assertNull("drops the givens", TangoPractice.start(puzzle, solution.map { it.other }, sign, kind))
            assertNull("wrong size", TangoPractice.start(puzzle, solution.drop(1), sign, kind))
        }
    }

    @Test
    fun `a point where no rung fires starts no drill, and Show me reveals the square`() {
        val puzzle = revealPuzzle
        val solution = TangoUniqueness.solve(puzzle)!!
        var grid = openGrid(puzzle)
        var last: Int? = null
        var revealed: TangoPracticeStep? = null
        while (revealed == null && grid.any { it == null }) {
            val step = checkNotNull(TangoPractice.show(puzzle, solution, grid, last))
            if (step.rung == null) revealed = step
            grid = grid.toMutableList().also { cells -> step.squares.forEach { cells[it.idx] = it.value } }
            last = step.squares.last().idx
        }
        val reveal = checkNotNull(revealed) { "the puzzle reaches a point no rung explains" }
        val square = reveal.squares.single()
        assertTrue(square.shown is TangoStep.Reveal)
        val before = grid.toMutableList().also { it[square.idx] = null }
        assertNull("no rung fires there", lowestRung(puzzle, before))

        val right = TangoPractice.judge(puzzle, solution, before, TechniqueId("sign"), square.idx, square.value)
        assertEquals(TangoPracticeVerdict.Right(reveal, trained = false), right)
        val wrong = TangoPractice.judge(puzzle, solution, before, TechniqueId("sign"), square.idx, square.value.other)
        assertEquals(TangoPracticeVerdict.Wrong(rung = null), wrong)
        techniques.forEach { technique ->
            TangoPractice.start(puzzle, solution, technique, BoardKind.Focus)?.let { start -> assertNotNull(lowestRung(puzzle, start)) }
        }
    }

    @Test
    fun `a grid where no rung fires is done for either kind, whatever the technique`() {
        val puzzle = revealPuzzle
        val solution = TangoUniqueness.solve(puzzle)!!
        val stalled = TangoPractice.walk(puzzle, solution).filter { it.technique == null }.toList()
        assertTrue("the puzzle reaches a point no rung explains", stalled.isNotEmpty())
        for (point in stalled) for (technique in techniques) for (kind in BoardKind.entries) {
            assertTrue("${technique.value} $kind", TangoPractice.isDone(puzzle, point.board, technique, kind))
        }
    }

    @Test
    fun `a move is Right in any instance of the easiest rung, Easier outside it, Wrong off the solution`() {
        var rightOnLaterInstance = 0
        var easier = 0
        var wrong = 0
        for ((name, technique, grid) in starts()) {
            val puzzle = dailies.getValue(name)
            val solution = TangoUniqueness.solve(puzzle)!!
            val rung = checkNotNull(lowestRung(puzzle, grid))
            val rule = TangoLadder.rungs.first { it.first == rung }.second
            val easiest = rule.forced(puzzle, grid)
            val firstIdx = easiest.minOf { it.idx }
            for (idx in grid.indices.filter { grid[it] == null }) {
                for (value in TangoCell.entries) {
                    val label = "$name ${technique.value} cell $idx $value"
                    val verdict = TangoPractice.judge(puzzle, solution, grid, technique, idx, value)
                    when {
                        value != solution[idx] -> {
                            assertEquals(label, TangoPracticeVerdict.Wrong(rung), verdict)
                            wrong++
                        }
                        easiest.any { it.idx == idx && it.value == value } -> {
                            val right = verdict as TangoPracticeVerdict.Right
                            assertEquals(label, rung, right.step.rung)
                            assertTrue(label, right.step.contains(idx, value))
                            assertEquals(label, rung == TangoStepRung.entries.first { it.trainerId == technique }, right.trained)
                            if (idx != firstIdx) rightOnLaterInstance++
                        }
                        else -> {
                            val easy = verdict as TangoPracticeVerdict.Easier
                            assertEquals(label, rung, easy.rung)
                            assertTrue("$label: shows a deduction of the rung", easy.step.rung == rung && easy.step.squares.all { sq -> easiest.any { it.idx == sq.idx } })
                            easier++
                        }
                    }
                }
            }
        }
        assertTrue("a move on a later instance was Right", rightOnLaterInstance > 0)
        assertTrue("a fitting move outside the rung was Easier", easier > 0)
        assertTrue("a move off the solution was Wrong", wrong > 0)
    }

    /** Show me's steps from the givens to the solved board, each with the board it was played on. */
    private fun showWalk(puzzle: TangoPuzzle, solution: List<TangoCell>): List<Pair<List<TangoCell?>, TangoPracticeStep>> {
        var grid = openGrid(puzzle)
        var last: Int? = null
        val walk = mutableListOf<Pair<List<TangoCell?>, TangoPracticeStep>>()
        while (grid.any { it == null }) {
            val step = checkNotNull(TangoPractice.show(puzzle, solution, grid, last)) { "a step on $grid" }
            walk += grid to step
            grid = grid.toMutableList().also { cells -> step.squares.forEach { cells[it.idx] = it.value } }
            last = step.squares.last().idx
            assertTrue("bounded", walk.size <= puzzle.cellCount)
        }
        return walk
    }

    @Test
    fun `a deduction that settles several squares is Right square by square, each with the whole step`() {
        val (name, grid, step) = dailies.firstNotNullOf { (name, puzzle) ->
            showWalk(puzzle, TangoUniqueness.solve(puzzle)!!).firstOrNull { it.second.squares.size > 1 }
                ?.let { Triple(name, it.first, it.second) }
        }
        val puzzle = dailies.getValue(name)
        val solution = TangoUniqueness.solve(puzzle)!!

        val verdicts = step.squares.map { TangoPractice.judge(puzzle, solution, grid, step.rung!!.trainerId, it.idx, it.value) }

        assertTrue(verdicts.all { it is TangoPracticeVerdict.Right && it.step.rung == step.rung && it.trained })
    }

    @Test
    fun `a move on a filled square or a given is refused`() {
        val puzzle = dailies.getValue("byid-1")
        val solution = TangoUniqueness.solve(puzzle)!!
        val grid = TangoPractice.start(puzzle, solution, TechniqueId("pair"), BoardKind.Focus)!!
        val given = puzzle.givens.keys.first()
        val filled = grid.indices.first { grid[it] != null && !puzzle.isGiven(it) }

        listOf(given, filled).forEach { idx ->
            try {
                TangoPractice.judge(puzzle, solution, grid, TechniqueId("sign"), idx, solution[idx])
                fail("cell $idx takes no move")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message!!, "$idx" in expected.message!!)
            }
        }
    }

    @Test
    fun `Show me plays the easiest rung whole, from the givens to a solved board`() {
        for ((name, puzzle) in dailies) {
            val solution = TangoUniqueness.solve(puzzle)!!
            val walk = showWalk(puzzle, solution)
            val filled = openGrid(puzzle).toMutableList()
            for ((grid, step) in walk) {
                assertEquals("$name: the easiest rung", lowestRung(puzzle, grid), step.rung)
                assertTrue("$name: squares are empty and right", step.squares.all { grid[it.idx] == null && it.value == solution[it.idx] })
                step.squares.forEach { filled[it.idx] = it.value }
            }
            assertEquals(name, solution, filled)
            assertNull(name, TangoPractice.show(puzzle, solution, filled, walk.last().second.squares.last().idx))
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

        /** Byid-758 with givens and signs dropped one by one while it stayed uniquely solvable; the stepper reveals a cell on it. */
        val revealPuzzle: TangoPuzzle by lazy {
            val p = dailies.getValue("byid-758")
            TangoPuzzle(
                variant = p.variant,
                n = p.n,
                givens = mapOf(
                    1 to TangoCell.MOON, 2 to TangoCell.SUN, 4 to TangoCell.MOON, 5 to TangoCell.SUN,
                    6 to TangoCell.MOON, 10 to TangoCell.SUN, 11 to TangoCell.MOON,
                ),
                markers = listOf(
                    TangoMarker(24, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
                    TangoMarker(28, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE),
                    TangoMarker(30, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
                    TangoMarker(32, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE),
                ),
            )
        }
    }
}
