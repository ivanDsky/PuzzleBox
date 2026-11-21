package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where a focus and a review board open, and when each is done (spec §2.2, [BoardKind]). */
class SudokuPracticeKindTest {

    private val order = SudokuTechniqueIds.all

    private fun points(puzzle: SudokuPuzzle) = SudokuPractice.walk(puzzle).toList()

    /** The runs of consecutive indices of [points] whose rung satisfies [inRun]. */
    private fun runs(points: List<SudokuPractice.WalkPoint>, inRun: (TechniqueId) -> Boolean): List<List<Int>> {
        val runs = ArrayList<List<Int>>()
        var run = ArrayList<Int>()
        points.forEachIndexed { i, point ->
            if (point.rung?.let(inRun) == true) {
                run += i
            } else {
                if (run.isNotEmpty()) runs += run
                run = ArrayList()
            }
        }
        if (run.isNotEmpty()) runs += run
        return runs
    }

    /** The index a focus board opens at, found by a plain scan: the first of the runs with the most points. */
    private fun expectedFocus(points: List<SudokuPractice.WalkPoint>, technique: TechniqueId): Int? =
        runs(points) { it == technique }.fold(emptyList<Int>()) { best, run -> if (run.size > best.size) run else best }.firstOrNull()

    /** The index a review board opens at: the first trained point of the first run of learned points holding the most trained ones. */
    private fun expectedReview(points: List<SudokuPractice.WalkPoint>, technique: TechniqueId): Int? {
        val learned = order.take(order.indexOf(technique) + 1)
        return runs(points) { it in learned }
            .map { run -> run.filter { points[it].rung == technique } }
            .fold(emptyList<Int>()) { best, trained -> if (trained.size > best.size) trained else best }
            .firstOrNull()
    }

    @Test
    fun `a focus board opens at the start of the longest run of the trained technique, the earliest on a tie`() {
        var notTheFirstRun = 0
        for ((name, puzzle) in SudokuTrainerFixtures.corpus) {
            val points = points(puzzle)
            for (technique in order) {
                val expected = expectedFocus(points, technique)
                assertEquals("$name ${technique.value}", expected?.let { points[it].boardBefore }, SudokuPractice.start(puzzle, technique, BoardKind.Focus))
                if (expected != null && expected != points.indexOfFirst { it.rung == technique }) notTheFirstRun++
            }
        }
        assertTrue("some puzzle's longest run is not its first", notTheFirstRun > 0)
    }

    @Test
    fun `a review board opens at the first trained point of the learned run with the most trained points`() {
        var afterLearned = 0
        for ((name, puzzle) in SudokuTrainerFixtures.corpus) {
            val points = points(puzzle)
            for (technique in order) {
                val expected = expectedReview(points, technique)
                assertEquals("$name ${technique.value}", expected?.let { points[it].boardBefore }, SudokuPractice.start(puzzle, technique, BoardKind.Review))
                if (expected != null && expected > 0 && points[expected - 1].rung?.let { order.indexOf(it) < order.indexOf(technique) } == true) afterLearned++
            }
        }
        assertTrue("some review board opens right after learned moves", afterLearned > 0)
    }

    @Test
    fun `both kinds open exactly where the other does`() {
        for ((name, puzzle) in SudokuTrainerFixtures.corpus) {
            for (technique in order) {
                val focus = SudokuPractice.start(puzzle, technique, BoardKind.Focus) != null
                val review = SudokuPractice.start(puzzle, technique, BoardKind.Review) != null
                assertEquals("$name ${technique.value}", focus, review)
            }
        }
    }

    @Test
    fun `a focus board is done when the easiest rung is another technique, solved or not`() {
        var unsolved = 0
        for ((name, puzzle) in SudokuTrainerFixtures.all) {
            for (technique in order) {
                for (point in points(puzzle)) {
                    val done = SudokuPractice.isDone(puzzle, point.boardBefore, technique, BoardKind.Focus)
                    assertEquals("$name ${technique.value} ${point.rung}", point.rung != technique, done)
                    if (done) unsolved++
                }
            }
        }
        assertTrue("a board is done before it is solved", unsolved > 0)
    }

    @Test
    fun `a review board is done when the easiest rung is a later technique, and goes on for a learned one`() {
        var learned = 0
        var later = 0
        for ((name, puzzle) in SudokuTrainerFixtures.all) {
            for (technique in order) {
                for (point in points(puzzle)) {
                    val rung = point.rung ?: continue
                    val done = SudokuPractice.isDone(puzzle, point.boardBefore, technique, BoardKind.Review)
                    assertEquals("$name ${technique.value} $rung", order.indexOf(rung) > order.indexOf(technique), done)
                    if (done) later++ else if (rung != technique) learned++
                }
            }
        }
        assertTrue("an earlier technique keeps the board going", learned > 0)
        assertTrue("a later technique ends it", later > 0)
    }

    @Test
    fun `a board no technique applies to is done for both kinds, where a walk stalls`() {
        var stalled = 0
        for ((name, puzzle) in SudokuTrainerFixtures.corpus) {
            val stall = points(puzzle).lastOrNull()?.takeIf { it.rung == null } ?: continue
            stalled++
            for (technique in order) for (kind in BoardKind.entries) {
                assertTrue("$name ${technique.value} $kind", SudokuPractice.isDone(puzzle, stall.boardBefore, technique, kind))
            }
        }
        assertTrue("some puzzle stalls the ladder", stalled > 0)
    }

    @Test
    fun `a solved board is done for both kinds`() {
        val puzzle = SudokuTrainerFixtures.dailies.getValue("li:1")
        val given = SudokuBoard.of(puzzle)
        val solved = given.cells.indices.fold(given) { acc, idx ->
            if (acc.digitAt(idx) == 0) acc.with(idx, SudokuCell.Entry(puzzle.solution[idx])) else acc
        }
        assertTrue(solved.isFull)
        for (kind in BoardKind.entries) assertTrue(kind.name, SudokuPractice.isDone(puzzle, solved, SudokuTechniqueIds.lastCell, kind))
    }
}
