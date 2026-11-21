package com.ivandsky.puzzlebox.games.queens.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.QueensTestSupport
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real daily 916 (7×7, solution 6, 8, 17, 26, 30, 39, 42): its walk is singles (a queen on 6), line-lock (crossing 7,
 * 14, 21, 28, the simplest of two instances: the other crosses 9, 10, 11), then singles six times (8, 17, 26, 39, 42,
 * 30). So the first singles point is not the longest singles run, and once the line-lock is made the easiest rung is
 * singles for good. Unique (8×8) is walked by every rung but the leftover queen.
 */
class QueensPracticeTest {

    private val real = QueensTestSupport.daily("queens-daily-real-916.json")
    private val unique = QueensTestSupport.daily("queens-daily-unique.json")
    private val sample = QueensTestSupport.daily("queens-daily-sample.json")

    private val lineLockStart = QueensBoard(queens = listOf(Cell(6)))
    private val singlesAgain = QueensBoard(queens = listOf(Cell(6)), manual = setOf(7, 14, 21, 28).map(::Cell).toSet())
    private val focus = BoardKind.Focus
    private val review = BoardKind.Review

    @Test
    fun `a focus board opens at the start of the longest run where the technique is the easiest rung`() {
        // The first singles point is the empty board, a run of one; the run after the line-lock has six.
        assertEquals(singlesAgain, QueensPractice.start(real, TechniqueId("singles"), focus))
        assertEquals(lineLockStart, QueensPractice.start(real, TechniqueId("line-lock"), focus))
    }

    @Test
    fun `a review board opens at the first trained point of the learned run with the most trained points`() {
        // Singles learned alone: the same two runs as the focus board. With line-lock learned too the walk is one run.
        assertEquals(singlesAgain, QueensPractice.start(real, TechniqueId("singles"), review))
        assertEquals(lineLockStart, QueensPractice.start(real, TechniqueId("line-lock"), review))
    }

    @Test
    fun `both kinds open exactly where the spec's runs say, on every daily and technique`() {
        val order = QueensLadder.Stepwise.rungs.mapNotNull { it.trainerId }
        for ((name, puzzle) in listOf("real" to real, "unique" to unique, "sample" to sample)) {
            // Attack points have no technique and never break a run: they are left out of the walk.
            val points = QueensPractice.walk(puzzle).filter { it.second != null }.toList()
            for (kind in listOf(focus, review)) {
                for (technique in order) {
                    val trained = order.indexOf(technique)
                    val asked = points.map { (_, easiest) -> order.indexOf(easiest).let { if (kind == focus) it == trained else it <= trained } }
                    val runs = mutableListOf<IntRange>()
                    var at = 0
                    while (at < points.size) {
                        if (!asked[at]) {
                            at++
                            continue
                        }
                        val first = at
                        while (at < points.size && asked[at]) at++
                        runs += first until at
                    }
                    fun IntRange.trainedPoints() = count { points[it].second == technique }
                    val run = runs.filter { it.trainedPoints() > 0 }.maxByOrNull { it.trainedPoints() } // the first of equals
                    val expected = run?.first { points[it].second == technique }?.let { points[it].first }
                    assertEquals("$name $kind ${technique.value}", expected, QueensPractice.start(puzzle, technique, kind))
                }
            }
        }
    }

    @Test
    fun `at every start the trained technique is the easiest rung`() {
        val techniques = listOf(
            "singles", "line-lock", "shared-squares", "pigeonhole-regions", "pigeonhole-lines", "squeeze",
            "count-squeeze", "short-look-ahead", "long-look-ahead",
        )
        for (kind in listOf(focus, review)) {
            for (name in techniques) {
                val start = QueensPractice.start(unique, TechniqueId(name), kind) ?: continue
                val easiest = QueensPractice.show(unique, start)
                assertEquals("$kind $name", TechniqueId(name), easiest?.unit?.rung?.trainerId)
            }
        }
    }

    @Test
    fun `no practice where the walk never meets the technique, for an unknown technique or without a true solution`() {
        for (kind in listOf(focus, review)) {
            assertNull(QueensPractice.start(real, TechniqueId("pigeonhole-regions"), kind))
            assertNull(QueensPractice.start(unique, TechniqueId("leftover-queen"), kind))
            assertNull(QueensPractice.start(real, TechniqueId("attack"), kind))
            assertNull(QueensPractice.start(real.copy(solution = null), TechniqueId("singles"), kind))
        }
    }

    @Test
    fun `a focus board is done when the easiest rung is another technique, solved or not`() {
        val lineLock = TechniqueId("line-lock")
        assertFalse(QueensPractice.isDone(real, lineLockStart, lineLock, focus))
        assertTrue(QueensPractice.isDone(real, singlesAgain, lineLock, focus))
        assertFalse(QueensPractice.isDone(real, QueensBoard(), TechniqueId("singles"), focus))
        assertTrue(QueensPractice.isDone(real, lineLockStart, TechniqueId("singles"), focus))
        assertTrue(QueensPractice.isDone(real, QueensBoard(queens = real.solution!!), TechniqueId("singles"), focus))
    }

    @Test
    fun `a review board is done when the easiest rung is a later technique, solved or not`() {
        assertFalse(QueensPractice.isDone(real, singlesAgain, TechniqueId("line-lock"), review))
        assertFalse(QueensPractice.isDone(real, lineLockStart, TechniqueId("line-lock"), review))
        assertTrue(QueensPractice.isDone(real, lineLockStart, TechniqueId("singles"), review))
        assertFalse(QueensPractice.isDone(real, singlesAgain, TechniqueId("singles"), review))
        assertTrue(QueensPractice.isDone(real, QueensBoard(queens = real.solution!!), TechniqueId("line-lock"), review))
    }

    @Test
    fun `show makes the easiest rung's deduction, not the stepper's pick`() {
        // The stepper opens real-916 with a line-lock; singles already proves 6.
        val first = QueensPractice.show(real, QueensBoard())
        assertEquals(TechniqueId("singles"), first?.unit?.rung?.trainerId)
        assertEquals(QueensStep.Deduction.Result.LastCell(Cell(6)), first?.result)

        val crossing = QueensPractice.show(real, lineLockStart)!!
        assertEquals(listOf(7, 14, 21, 28), QueensPractice.crossings(real, lineLockStart, crossing).map { it.idx })
        assertNull(QueensPractice.show(real, QueensBoard(queens = real.solution!!)))
    }

    @Test
    fun `any instance of the easiest rung is right, and a cross-out leaves its other squares to make`() {
        val shown = QueensPractice.judge(real, lineLockStart, QueensPracticeMove.Cross(Cell(7)))
        assertEquals(TechniqueId("line-lock"), shown?.rung?.trainerId)
        val own = shown as QueensPracticeVerdict.Right
        assertEquals(setOf(7, 14, 21, 28), own.step.crossed())
        assertEquals(setOf(14, 21, 28).map(::Cell).toSet(), own.remaining)

        // 9, 10, 11 is a line-lock too, though not the one Show me makes.
        val other = QueensPractice.judge(real, lineLockStart, QueensPracticeMove.Cross(Cell(10))) as QueensPracticeVerdict.Right
        assertEquals(setOf(9, 10, 11), other.step.crossed())
        assertEquals(setOf(9, 11).map(::Cell).toSet(), other.remaining)
    }

    @Test
    fun `a queen is right when singles places it`() {
        val verdict = QueensPractice.judge(real, singlesAgain, QueensPracticeMove.Place(Cell(8))) as QueensPracticeVerdict.Right
        assertEquals(TechniqueId("singles"), verdict.rung.trainerId)
        assertEquals(QueensStep.Deduction.Result.LastCell(Cell(8)), verdict.step.result)
        assertTrue(verdict.remaining.isEmpty())
    }

    @Test
    fun `a move that fits the solution but the easiest rung does not make is easier, and names that rung's deduction`() {
        for (move in listOf(QueensPracticeMove.Place(Cell(8)), QueensPracticeMove.Cross(Cell(15)))) {
            val verdict = QueensPractice.judge(real, lineLockStart, move) as QueensPracticeVerdict.Easier
            assertEquals(TechniqueId("line-lock"), verdict.rung.trainerId)
            assertNotNull(verdict.step.crossed().takeIf { it.isNotEmpty() })
        }
        // Once the line-lock is made, singles is the easiest rung: a cross that fits is easier than it.
        val crossed = QueensPractice.judge(real, singlesAgain, QueensPracticeMove.Cross(Cell(15))) as QueensPracticeVerdict.Easier
        assertEquals(TechniqueId("singles"), crossed.rung.trainerId)
    }

    @Test
    fun `a move that breaks the solution is wrong, for the reason it breaks it`() {
        fun why(move: QueensPracticeMove) = (QueensPractice.judge(real, lineLockStart, move) as QueensPracticeVerdict.Wrong).why
        assertEquals(QueensPracticeVerdict.Wrong.Why.ON_SOLUTION, why(QueensPracticeMove.Cross(Cell(8))))
        assertEquals(QueensPracticeVerdict.Wrong.Why.ATTACKED, why(QueensPracticeMove.Place(Cell(5))))
        assertEquals(QueensPracticeVerdict.Wrong.Why.OFF_SOLUTION, why(QueensPracticeMove.Place(Cell(14))))
        val wrong = QueensPractice.judge(real, lineLockStart, QueensPracticeMove.Cross(Cell(8)))
        assertEquals(TechniqueId("line-lock"), wrong?.rung?.trainerId)
    }

    private fun QueensStep.Deduction.crossed(): Set<Int> =
        (result as? QueensStep.Deduction.Result.Cross)?.cells.orEmpty().map { it.idx }.toSet()
}
