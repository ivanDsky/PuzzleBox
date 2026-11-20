package com.ivandsky.puzzlebox.games.queens.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.QueensTestSupport
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real daily 916 (7×7, solution 6, 8, 17, 26, 30, 39, 42): singles proves 6 on the empty board, then a queen on 6
 * leaves line-lock the easiest rung, with two instances: crossing 7, 14, 21, 28, and crossing 9, 10, 11. Once the
 * first is crossed, singles places 8. Unique (8×8) is walked by every rung but the leftover queen.
 */
class QueensPracticeTest {

    private val real = QueensTestSupport.daily("queens-daily-real-916.json")
    private val unique = QueensTestSupport.daily("queens-daily-unique.json")

    private val lineLockStart = QueensBoard(queens = listOf(Cell(6)))
    private val singlesAgain = QueensBoard(queens = listOf(Cell(6)), manual = setOf(7, 14, 21, 28).map(::Cell).toSet())

    @Test
    fun `a practice starts at the first board of the easiest-rung walk where the technique is the easiest rung`() {
        assertEquals(QueensBoard(), QueensPractice.start(real, TechniqueId("singles")))
        assertEquals(lineLockStart, QueensPractice.start(real, TechniqueId("line-lock")))
        assertEquals(QueensBoard(), QueensPractice.start(unique, TechniqueId("squeeze")))
        assertEquals(QueensBoard(manual = setOf(Cell(2))), QueensPractice.start(unique, TechniqueId("short-look-ahead")))
    }

    @Test
    fun `at every start the trained technique is the easiest rung`() {
        val techniques = listOf(
            "singles", "line-lock", "shared-squares", "pigeonhole-regions", "pigeonhole-lines", "squeeze",
            "count-squeeze", "short-look-ahead", "long-look-ahead",
        )
        for (name in techniques) {
            val start = QueensPractice.start(unique, TechniqueId(name)) ?: continue
            val easiest = QueensPractice.show(unique, start)
            assertEquals(name, TechniqueId(name), easiest?.unit?.rung?.trainerId)
        }
    }

    @Test
    fun `no practice where the walk never meets the technique, for an unknown technique or without a true solution`() {
        assertNull(QueensPractice.start(real, TechniqueId("pigeonhole-regions")))
        assertNull(QueensPractice.start(unique, TechniqueId("leftover-queen")))
        assertNull(QueensPractice.start(real, TechniqueId("attack")))
        assertNull(QueensPractice.start(real.copy(solution = null), TechniqueId("singles")))
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
