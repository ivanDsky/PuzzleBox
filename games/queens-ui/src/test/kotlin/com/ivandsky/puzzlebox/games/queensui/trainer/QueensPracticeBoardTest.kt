package com.ivandsky.puzzlebox.games.queensui.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.engine.trainer.QueensPractice
import com.ivandsky.puzzlebox.games.queensui.hint.QueensSpotlightMapper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The practice board on real daily 916 (7×7, solution 6, 8, 17, 26, 30, 39, 42), played through the play board's own
 * state, tap by tap and drag by drag. The line-lock practice starts with a queen on 6 and no crosses. Line-lock is the
 * easiest rung there, crossing 7, 14, 21, 28 (or 9, 10, 11); with the first crossed, singles places 8, 17, 26, 39, 42
 * and 30 in turn.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QueensPracticeBoardTest {

    private val verdicts = mutableListOf<TrainerVerdict>()
    private val line = listOf(7, 14, 21, 28)

    private fun TestScope.practice(technique: String = "line-lock"): QueensPracticeBoard =
        QueensTrainerFixtures.trainer.practice(QueensTrainerFixtures.canonical, TechniqueId(technique), backgroundScope, verdicts::add)
            as QueensPracticeBoard

    private fun QueensPracticeBoard.drag(vararg cells: Int) {
        state.onDragStart(cells.first())
        cells.drop(1).forEach(state::onDragCell)
        state.onDragEnd()
    }

    private fun QueensPracticeBoard.queenOn(cell: Int) {
        state.onTap(cell)
        state.onTap(cell)
    }

    private fun TestScope.settle() {
        testScheduler.advanceTimeBy(SETTLE_PAUSE_MS)
        testScheduler.runCurrent()
    }

    private val QueensPracticeBoard.board: QueensBoard get() = state.session.board

    @Test
    fun `a practice opens on the walk's board, nothing played`() = runTest {
        val board = practice()

        assertEquals(QueensBoard(queens = listOf(Cell(6))), board.board)
        assertEquals(PracticeStatus.FindNextMove, board.status.value)
        assertTrue(!board.state.canUndo)
        assertEquals(QueensBoard(), practice("singles").board)
    }

    @Test
    fun `no practice for a technique the puzzle never needs first`() = runTest {
        val none = QueensTrainerFixtures.trainer.practice(
            QueensTrainerFixtures.canonical, TechniqueId("pigeonhole-regions"), backgroundScope, verdicts::add,
        )

        assertNull(none)
    }

    @Test
    fun `a drag along the line is right square by square and counts once, with the hint's own Spotlight`() = runTest {
        val board = practice()

        board.drag(*line.toIntArray())

        assertEquals(QueensBoard(queens = listOf(Cell(6)), manual = line.map(::Cell).toSet()), board.board)
        assertEquals(listOf(TrainerVerdict.Right), verdicts)
        val status = board.status.value as PracticeStatus.Right
        assertTrue(status.trained)
        val puzzle = QueensTrainerFixtures.puzzle
        val step = QueensPractice.show(puzzle, QueensBoard(queens = listOf(Cell(6))))!!
        assertEquals(QueensSpotlightMapper.map(step, puzzle, playId = 0), status.spotlight.copy(playId = 0))
    }

    @Test
    fun `crossing the line tap by tap counts once as well`() = runTest {
        val board = practice()

        line.forEach(board.state::onTap)
        settle()

        assertEquals(line.map(::Cell).toSet(), board.board.manual)
        assertEquals(listOf(TrainerVerdict.Right), verdicts)
    }

    @Test
    fun `another instance of the easiest rung is right, shown with its own squares`() = runTest {
        val board = practice()

        listOf(10, 9, 11).forEach(board.state::onTap)
        settle()

        assertEquals(setOf(9, 10, 11).map(::Cell).toSet(), board.board.manual)
        assertEquals(listOf(TrainerVerdict.Right), verdicts)
        val status = board.status.value as PracticeStatus.Right
        assertTrue(status.spotlight.roles.keys.containsAll(listOf(9, 10, 11)))
    }

    @Test
    fun `a cross that breaks the solution stays on view until the pause, then is undone to the exact board`() = runTest {
        val board = practice()
        val before = board.state.session

        board.state.onTap(8)
        assertTrue(board.state.session.markAt(Cell(8)) != null)
        settle()

        assertSame(before, board.state.session)
        assertEquals(QueensBoard(queens = listOf(Cell(6))), board.board)
        assertTrue(board.status.value is PracticeStatus.Wrong)
        assertTrue((board.status.value as PracticeStatus.Wrong).why.isNotBlank())
        assertEquals(listOf(TrainerVerdict.Wrong), verdicts)
    }

    @Test
    fun `a queen that breaks the solution is undone at once, leaving no marks`() = runTest {
        val board = practice()
        val before = board.state.session

        board.queenOn(14)

        assertSame(before, board.state.session)
        assertTrue(board.state.session.queens == listOf(Cell(6)))
        assertTrue(board.status.value is PracticeStatus.Wrong)
        assertEquals(listOf(TrainerVerdict.Wrong), verdicts)
    }

    @Test
    fun `a move that fits but is not part of the easiest rung is undone as easier, naming it`() = runTest {
        val board = practice()
        val before = board.state.session

        board.state.onTap(15)
        settle()

        assertSame(before, board.state.session)
        val easier = board.status.value as PracticeStatus.Easier
        assertEquals(QueensTrainerCatalog.name(TechniqueId("line-lock")), easier.technique)

        board.queenOn(8)
        assertSame(before, board.state.session)
        assertTrue(board.status.value is PracticeStatus.Easier)
        assertEquals(listOf(TrainerVerdict.Wrong, TrainerVerdict.Wrong), verdicts)
    }

    @Test
    fun `an undone move on another rung reports nothing`() = runTest {
        val board = practice()
        board.drag(*line.toIntArray())
        val before = board.state.session

        board.state.onTap(15)
        settle()

        assertSame(before, board.state.session)
        val easier = board.status.value as PracticeStatus.Easier
        assertEquals(QueensTrainerCatalog.name(TechniqueId("singles")), easier.technique)
        assertEquals(listOf(TrainerVerdict.Right), verdicts)
    }

    @Test
    fun `a drag line stops at the first move that is not right, and the rest of it paints nothing`() = runTest {
        val board = practice()
        board.state.onDragStart(7)
        board.state.onDragCell(14)
        val beforeWrong = board.state.session

        board.state.onDragCell(17)
        board.state.onDragCell(21)
        board.state.onDragEnd()

        assertSame(beforeWrong, board.state.session)
        assertEquals(setOf(7, 14).map(::Cell).toSet(), board.board.manual)
        assertTrue(board.status.value is PracticeStatus.Wrong)
        // With 7 crossed singles is the easiest rung (it places 8): the undone cross is not the trained rung's turn.
        assertEquals(listOf(TrainerVerdict.Right), verdicts)

        board.drag(21, 28)
        assertEquals(line.map(::Cell).toSet(), board.board.manual)
    }

    @Test
    fun `a drag that starts wrong is undone at its first square and counted as the trained rung's wrong`() = runTest {
        val board = practice()
        val before = board.state.session

        board.drag(8, 15)

        assertSame(before, board.state.session)
        assertTrue(board.status.value is PracticeStatus.Wrong)
        assertEquals(listOf(TrainerVerdict.Wrong), verdicts)
    }

    @Test
    fun `tapping twice for a queen keeps it, and the cross it passed through is never judged`() = runTest {
        val board = practice()
        board.drag(*line.toIntArray())

        board.queenOn(8)
        settle()

        assertTrue(Cell(8) in board.state.session.queens)
        assertEquals(PracticeStatus.Right::class, board.status.value::class)
        assertTrue(!(board.status.value as PracticeStatus.Right).trained)
        assertEquals(listOf(TrainerVerdict.Right), verdicts)
    }

    @Test
    fun `touching another square closes a tap cycle, the held cross judged first`() = runTest {
        val board = practice()

        board.state.onTap(8)
        board.state.onTap(15)

        assertTrue(board.state.session.markAt(Cell(8)) == null)
        assertTrue(board.state.session.markAt(Cell(15)) != null)
        assertEquals(listOf(TrainerVerdict.Wrong), verdicts)
    }

    @Test
    fun `show me makes the whole easiest step and counts only for the trained technique`() = runTest {
        val board = practice()

        board.showMe()

        assertEquals(line.map(::Cell).toSet(), board.board.manual)
        assertTrue(board.status.value is PracticeStatus.Shown)
        assertEquals(listOf(TrainerVerdict.ShowMe), verdicts)

        board.showMe() // singles places 8: another rung, so no count
        assertTrue(Cell(8) in board.state.session.queens)
        assertEquals(listOf(TrainerVerdict.ShowMe), verdicts)
    }

    @Test
    fun `the queens placed one by one solve the board`() = runTest {
        val board = practice()
        board.drag(*line.toIntArray())

        listOf(8, 17, 26, 39, 42, 30).forEach { board.queenOn(it) }

        assertTrue(board.state.session.isComplete)
        assertEquals(PracticeStatus.Solved, board.status.value)
        assertEquals(listOf(TrainerVerdict.Right), verdicts)
    }

    @Test
    fun `show me taken to the end solves the board`() = runTest {
        val board = practice()

        repeat(10) { if (board.status.value != PracticeStatus.Solved) board.showMe() }

        assertTrue(board.state.session.isComplete)
        assertEquals(PracticeStatus.Solved, board.status.value)
        assertEquals(listOf(TrainerVerdict.ShowMe), verdicts)
    }

    private companion object {
        /** Longer than the pause that closes a tap cycle. */
        const val SETTLE_PAUSE_MS = 1_000L
    }
}
