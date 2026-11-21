package com.ivandsky.puzzlebox.games.queensui.trainer

import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.engine.trainer.QueensPractice
import com.ivandsky.puzzlebox.games.queensui.hint.QueensSpotlightMapper
import kotlinx.coroutines.Dispatchers
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

    private fun TestScope.practice(technique: String = "line-lock", kind: BoardKind = BoardKind.Review): QueensPracticeBoard =
        QueensTrainerFixtures.trainer.practice(QueensTrainerFixtures.canonical, TechniqueId(technique), kind, backgroundScope, verdicts::add)
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
        assertEquals(QueensBoard(queens = listOf(Cell(6)), manual = line.map(::Cell).toSet()), practice("singles", BoardKind.Focus).board)
    }

    @Test
    fun `no practice for a technique the puzzle never needs first`() = runTest {
        for (kind in listOf(BoardKind.Focus, BoardKind.Review)) {
            val none = QueensTrainerFixtures.trainer.practice(
                QueensTrainerFixtures.canonical, TechniqueId("pigeonhole-regions"), kind, backgroundScope, verdicts::add,
            )
            assertNull(none)
        }
    }

    @Test
    fun `a drag along the line is right square by square, Right only with the last, and counts once, with the hint's own Spotlight`() = runTest {
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
    fun `a drag line of three crosses keeps going twice, then is right, counted at the last square`() = runTest {
        val board = practice()

        board.state.onDragStart(9)
        assertEquals(PracticeStatus.KeepGoing(left = 2, trained = true), board.status.value)
        board.state.onDragCell(10)
        assertEquals(PracticeStatus.KeepGoing(left = 1, trained = true), board.status.value)
        assertEquals(emptyList<TrainerVerdict>(), verdicts)
        board.state.onDragCell(11)
        board.state.onDragEnd()

        val right = board.status.value as PracticeStatus.Right
        assertTrue(right.trained)
        assertEquals(listOf(TrainerVerdict.Right), verdicts)
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
        assertEquals(listOf(TrainerVerdict.Wrong), verdicts)
    }

    @Test
    fun `while an explanation stands no move lands, none is judged, and Got it takes the board back`() = runTest {
        val board = practice()
        val before = board.state.session
        board.state.onTap(15)
        settle()
        val easier = board.status.value
        assertTrue(easier is PracticeStatus.Easier)

        board.drag(*line.toIntArray())
        board.state.onTap(14)
        settle()

        assertSame(before, board.state.session)
        assertEquals(easier, board.status.value)
        assertEquals(listOf(TrainerVerdict.Wrong), verdicts)

        board.gotIt()

        assertEquals(PracticeStatus.FindNextMove, board.status.value)
        board.drag(*line.toIntArray())
        assertEquals(line.map(::Cell).toSet(), board.board.manual)
        assertTrue(board.status.value is PracticeStatus.Right)
    }

    @Test
    fun `Got it does nothing when no explanation stands`() = runTest {
        val board = practice()

        board.gotIt()
        assertEquals(PracticeStatus.FindNextMove, board.status.value)

        board.drag(*line.toIntArray())
        val right = board.status.value
        board.gotIt()
        assertEquals(right, board.status.value)
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
        // With 7 crossed singles is the easiest rung (it places 8): the undone cross is not the trained rung's turn,
        // and the step in hand is not finished, so nothing is counted yet.
        assertEquals(emptyList<TrainerVerdict>(), verdicts)

        board.drag(21, 28)
        assertEquals(line.map(::Cell).toSet(), board.board.manual)
        assertEquals(listOf(TrainerVerdict.Right), verdicts)
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

        board.showMe() // an explanation stands: Show me does nothing until Got it
        assertEquals(line.map(::Cell).toSet(), board.board.manual)
        assertTrue(Cell(8) !in board.state.session.queens)

        board.gotIt()
        board.showMe() // singles places 8: another rung, so no count
        assertTrue(Cell(8) in board.state.session.queens)
        assertEquals(listOf(TrainerVerdict.ShowMe), verdicts)
    }

    @Test
    fun `the queens placed one by one solve the board, which is done with the last step to flash`() = runTest {
        val board = practice()
        board.drag(*line.toIntArray())

        listOf(8, 17, 26, 39, 42).forEach { board.queenOn(it) }
        assertTrue(board.status.value is PracticeStatus.Right)
        board.queenOn(30)

        assertTrue(board.state.session.isComplete)
        val done = board.status.value as PracticeStatus.Done
        val before = QueensBoard(queens = listOf(6, 8, 17, 26, 39, 42).map(::Cell), manual = line.map(::Cell).toSet())
        val step = QueensPractice.show(QueensTrainerFixtures.puzzle, before)!!
        assertEquals(QueensSpotlightMapper.map(step, QueensTrainerFixtures.puzzle, playId = 0), done.last?.copy(playId = 0))
        assertEquals(listOf(TrainerVerdict.Right), verdicts)
    }

    @Test
    fun `show me taken to the end ends in Shown, and Got it on that step is Done with nothing to flash`() = runTest {
        val board = practice()

        while (!board.state.session.isComplete) {
            board.gotIt()
            assertTrue(board.status.value is PracticeStatus.FindNextMove)
            board.showMe()
        }

        assertTrue(board.status.value is PracticeStatus.Shown)
        board.gotIt()
        assertEquals(PracticeStatus.Done(null), board.status.value)
        assertEquals(listOf(TrainerVerdict.ShowMe), verdicts)
    }

    @Test
    fun `a focus board is done with the line-lock, unsolved, and the last Right still counts`() = runTest {
        val board = practice(kind = BoardKind.Focus)

        board.drag(*line.toIntArray())

        val done = board.status.value as PracticeStatus.Done
        val puzzle = QueensTrainerFixtures.puzzle
        val step = QueensPractice.show(puzzle, QueensBoard(queens = listOf(Cell(6))))!!
        assertEquals(QueensSpotlightMapper.map(step, puzzle, playId = 0), done.last?.copy(playId = 0))
        assertEquals(listOf(TrainerVerdict.Right), verdicts)
        assertEquals(line.map(::Cell).toSet(), board.board.manual)
        assertTrue(!board.state.session.isComplete)
    }

    @Test
    fun `a done board refuses moves and Show me, judges nothing, and Got it changes nothing`() = runTest {
        val board = practice(kind = BoardKind.Focus)
        board.drag(*line.toIntArray())
        val done = board.status.value
        val after = board.state.session

        board.queenOn(8)
        board.state.onTap(15)
        settle()
        board.drag(9, 10, 11)
        board.showMe()
        board.gotIt()

        assertSame(after, board.state.session)
        assertEquals(done, board.status.value)
        assertEquals(listOf(TrainerVerdict.Right), verdicts)
    }

    @Test
    fun `a focus board's Show me on the step that ends it is Shown, then Got it makes it Done`() = runTest {
        val board = practice(kind = BoardKind.Focus)

        board.showMe()

        assertTrue(board.status.value is PracticeStatus.Shown)
        board.gotIt()
        assertEquals(PracticeStatus.Done(null), board.status.value)
        assertEquals(listOf(TrainerVerdict.ShowMe), verdicts)
        assertTrue(Cell(8) !in board.state.session.queens)
    }

    @Test
    fun `a focus board of singles opens at its longest run and ends when the puzzle is solved`() = runTest {
        val board = practice("singles", BoardKind.Focus)

        assertEquals(QueensBoard(queens = listOf(Cell(6)), manual = line.map(::Cell).toSet()), board.board)
        listOf(8, 17, 26, 39, 42).forEach { board.queenOn(it) }
        assertTrue(board.status.value is PracticeStatus.Right)
        board.queenOn(30)

        assertTrue(board.state.session.isComplete)
        assertTrue((board.status.value as PracticeStatus.Done).last != null)
        assertEquals(List(6) { TrainerVerdict.Right }, verdicts)
    }

    @Test
    fun `a review board keeps asking learned techniques after the trained one`() = runTest {
        val board = practice(kind = BoardKind.Review)

        board.drag(*line.toIntArray())
        assertTrue(board.status.value is PracticeStatus.Right)
        board.queenOn(8)

        val right = board.status.value as PracticeStatus.Right
        assertTrue(!right.trained)
        assertTrue(Cell(8) in board.state.session.queens)
    }

    @Test
    fun `a board whose position is already over at open is Done with nothing to flash and takes no move`() = runTest {
        // Singles reviewed from the line-lock position: the easiest rung there is a later technique.
        val board = QueensPracticeBoard(
            QueensTrainerFixtures.puzzle, TechniqueId("singles"), BoardKind.Review, QueensBoard(queens = listOf(Cell(6))),
            Dispatchers.Default, backgroundScope, verdicts::add,
        )
        val before = board.state.session

        assertEquals(PracticeStatus.Done(null), board.status.value)
        board.drag(*line.toIntArray())
        board.showMe()

        assertSame(before, board.state.session)
        assertEquals(emptyList<TrainerVerdict>(), verdicts)
    }

    private companion object {
        /** Longer than the pause that closes a tap cycle. */
        const val SETTLE_PAUSE_MS = 1_000L
    }
}
