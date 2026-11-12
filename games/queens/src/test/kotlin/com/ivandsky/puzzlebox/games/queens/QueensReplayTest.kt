package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.replay.ReplayFacts
import com.ivandsky.puzzlebox.game.replay.ReplayMove
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.AutoCross
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.Clear
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.Cross
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.Erase
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.Hint
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.Place
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.Redo
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.Remove
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove.Undo
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Queens replay adapter: every move kind, the persisted kind numbers and the board facts. */
class QueensReplayTest {

    private val at = 83_000L
    private val now = Instant.ofEpochMilli(at)

    /** 4×4 with the stored solution on cells 1, 7, 8, 14. */
    private val initial = QueensSession(ref = null, puzzle = QueensTestSupport.n4Puzzle())

    private fun wire(kind: Int, vararg args: Int) = ReplayMove(kind, args)

    private fun QueensSession.step(move: QueensReplayMove): QueensSession? =
        QueensReplay.step(this, move.toReplayMove(), at)

    private fun QueensSession.play(vararg moves: QueensReplayMove): QueensSession =
        moves.fold(this) { session, move -> checkNotNull(session.step(move)) { "$move was rejected" } }

    private fun assertRejected(session: QueensSession, vararg moves: ReplayMove) {
        for (move in moves) assertNull("$move", QueensReplay.step(session, move, at))
    }

    private fun factsOf(session: QueensSession): ReplayFacts = QueensReplay.factsOf(initial)(session)

    // ------------------------------------------------------------------ kinds

    @Test
    fun `place puts a queen on the cell`() {
        val placed = initial.step(Place(Cell(1)))

        assertEquals(initial.withQueenPlaced(Cell(1), now), placed)
        assertEquals(listOf(Cell(1)), placed?.queens)
    }

    @Test
    fun `place on a standing queen changes nothing`() {
        val placed = initial.play(Place(Cell(1)))

        assertSame(placed, placed.step(Place(Cell(1))))
    }

    @Test
    fun `place is rejected on a wrong arg count or a cell off the board`() {
        assertRejected(initial, wire(0), wire(0, 1, 2), wire(0, 16), wire(0, -1))
    }

    @Test
    fun `remove takes the queen off the cell`() {
        val placed = initial.play(Place(Cell(1)))

        val removed = placed.step(Remove(Cell(1)))

        assertEquals(placed.withQueenRemoved(Cell(1), now), removed)
        assertEquals(emptyList<Cell>(), removed?.queens)
    }

    @Test
    fun `remove on a queen-free cell changes nothing`() {
        assertSame(initial, initial.step(Remove(Cell(1))))
    }

    @Test
    fun `remove is rejected on a wrong arg count or a cell off the board`() {
        assertRejected(initial, wire(1), wire(1, 1, 2), wire(1, 16), wire(1, -1))
    }

    @Test
    fun `cross puts a manual mark on the cell`() {
        val crossed = initial.step(Cross(Cell(5)))

        assertEquals(initial.withCrossToggled(Cell(5), now), crossed)
        assertEquals(QueensMark(MarkKind.MANUAL), crossed?.markAt(Cell(5)))
    }

    @Test
    fun `cross on a manual mark changes nothing`() {
        val crossed = initial.play(Cross(Cell(5)))

        assertSame(crossed, crossed.step(Cross(Cell(5))))
    }

    @Test
    fun `cross on a queen changes nothing`() {
        val placed = initial.play(Place(Cell(1)))

        assertSame(placed, placed.step(Cross(Cell(1))))
    }

    @Test
    fun `cross is rejected on a wrong arg count or a cell off the board`() {
        assertRejected(initial, wire(2), wire(2, 1, 2), wire(2, 16), wire(2, -1))
    }

    @Test
    fun `erase takes the manual mark off the cell`() {
        val crossed = initial.play(Cross(Cell(5)))

        val erased = crossed.step(Erase(Cell(5), extend = false))

        assertEquals(crossed.withCrossErased(Cell(5), extendRun = false, now = now), erased)
        assertNull(erased?.markAt(Cell(5)))
    }

    @Test
    fun `erase off a manual mark changes nothing`() {
        val placed = initial.play(Place(Cell(1)))
        assertEquals(MarkKind.AUTO, placed.markAt(Cell(2))?.kind)

        assertSame(placed, placed.step(Erase(Cell(2), extend = false)))
        assertSame(placed, placed.step(Erase(Cell(10), extend = true)))
    }

    @Test
    fun `erase is rejected on a wrong arg count, an extend flag that is not 0 or 1 or a cell off the board`() {
        val crossed = initial.play(Cross(Cell(5)))

        assertRejected(crossed, wire(3), wire(3, 5), wire(3, 5, 0, 0), wire(3, 5, 2), wire(3, 5, -1), wire(3, 16, 0))
    }

    @Test
    fun `clear wipes queens and marks`() {
        val played = initial.play(Cross(Cell(5)), Place(Cell(1)))

        val cleared = played.step(Clear)

        assertEquals(played.withCleared(now), cleared)
        assertEquals(QueensBoard(), cleared?.board)
        assertEquals(emptyMap<Cell, QueensMark>(), cleared?.marks)
    }

    @Test
    fun `clear on an empty board changes nothing`() {
        assertSame(initial, initial.step(Clear))
    }

    @Test
    fun `undo reverts the last action`() {
        val placed = initial.play(Place(Cell(1)))

        val undone = placed.step(Undo)

        assertEquals(placed.withUndo(), undone)
        assertEquals(emptyList<Cell>(), undone?.queens)
    }

    @Test
    fun `undo with nothing done changes nothing`() {
        assertSame(initial, initial.step(Undo))
    }

    @Test
    fun `redo applies the undone action again`() {
        val undone = initial.play(Place(Cell(1)), Undo)

        val redone = undone.step(Redo)

        assertEquals(undone.withRedo(), redone)
        assertEquals(listOf(Cell(1)), redone?.queens)
    }

    @Test
    fun `redo with nothing undone changes nothing`() {
        val placed = initial.play(Place(Cell(1)))

        assertSame(placed, placed.step(Redo))
    }

    @Test
    fun `hint grows the counted hints by one`() {
        val hinted = initial.step(Hint)

        assertEquals(initial.withHintUsed(), hinted)
        assertEquals(1, hinted?.hintsUsed)
    }

    @Test
    fun `hint on a solved board changes nothing`() {
        val solved = solvedSession()

        assertSame(solved, solved.step(Hint))
    }

    @Test
    fun `clear, undo, redo and hint are rejected with any arg`() {
        val played = initial.play(Place(Cell(1)), Undo)

        assertRejected(played, wire(4, 0), wire(5, 0), wire(6, 0), wire(7, 0))
    }

    @Test
    fun `auto-cross off strips the system marks and on derives them again`() {
        val placed = initial.play(Place(Cell(1)))

        val off = placed.step(AutoCross(on = false))
        val on = off?.step(AutoCross(on = true))

        assertEquals(placed.withAutoCross(false), off)
        assertEquals(emptyMap<Cell, QueensMark>(), off?.marks)
        assertEquals(placed, on)
    }

    @Test
    fun `auto-cross at the value it already has changes nothing`() {
        assertSame(initial, initial.step(AutoCross(on = true)))
    }

    @Test
    fun `auto-cross is rejected on a wrong arg count or a value that is not 0 or 1`() {
        assertRejected(initial, wire(8), wire(8, 0, 0), wire(8, 2), wire(8, -1))
    }

    @Test
    fun `an unknown kind is rejected`() {
        assertRejected(initial, wire(9), wire(9, 1), wire(-1), wire(Int.MAX_VALUE))
    }

    // ------------------------------------------------------------------ persisted format

    private val wireFormat: List<Pair<QueensReplayMove, ReplayMove>> = listOf(
        Place(Cell(3)) to wire(0, 3),
        Remove(Cell(3)) to wire(1, 3),
        Cross(Cell(3)) to wire(2, 3),
        Erase(Cell(3), extend = false) to wire(3, 3, 0),
        Erase(Cell(3), extend = true) to wire(3, 3, 1),
        Clear to wire(4),
        Undo to wire(5),
        Redo to wire(6),
        Hint to wire(7),
        AutoCross(on = false) to wire(8, 0),
        AutoCross(on = true) to wire(8, 1),
    )

    @Test
    fun `every typed move keeps its persisted kind and args`() {
        for ((typed, recorded) in wireFormat) assertEquals(recorded, typed.toReplayMove())
    }

    @Test
    fun `every recorded move reads back as the typed move it was written from`() {
        for ((typed, recorded) in wireFormat) assertEquals(typed, QueensReplayMove.of(recorded, cellCount = 16))
    }

    // ------------------------------------------------------------------ step

    @Test
    fun `an erase swipe rebuilds one undo unit from the extend flag alone`() {
        val crossed = initial.play(Cross(Cell(5)), Cross(Cell(6)), Cross(Cell(9)))

        val swiped = crossed.play(
            Erase(Cell(5), extend = false),
            Erase(Cell(6), extend = true),
            Erase(Cell(9), extend = true),
        )

        val live = crossed
            .withCrossErased(Cell(5), extendRun = false, now = now)
            .withCrossErased(Cell(6), extendRun = true, now = now)
            .withCrossErased(Cell(9), extendRun = true, now = now)
        assertEquals(live, swiped)
        assertEquals(QueensCommand.EraseCrosses(setOf(Cell(5), Cell(6), Cell(9))), swiped.history.done.last())
        assertEquals(crossed.board, swiped.play(Undo).board)
    }

    @Test
    fun `an erase that does not extend starts its own undo unit`() {
        val crossed = initial.play(Cross(Cell(5)), Cross(Cell(6)))

        val erased = crossed.play(Erase(Cell(5), extend = false), Erase(Cell(6), extend = false))

        assertEquals(setOf(Cell(6)), erased.play(Undo).board.manual)
        assertEquals(crossed.board, erased.play(Undo, Undo).board)
    }

    @Test
    fun `the winning move is stamped with the move time`() {
        val solved = solvedSession()

        assertEquals(now, solved.completedAt)
    }

    @Test
    fun `a step never throws at the ends of the time range`() {
        for (extreme in listOf(Long.MIN_VALUE, Long.MAX_VALUE)) {
            val placed = QueensReplay.step(initial, Place(Cell(1)).toReplayMove(), extreme)

            assertEquals(listOf(Cell(1)), placed?.queens)
        }
    }

    @Test
    fun `a scripted solve through every kind ends on a solved board`() {
        val script = listOf(
            AutoCross(on = false),
            Cross(Cell(1)),
            Place(Cell(1)),
            Hint,
            Place(Cell(2)),
            Remove(Cell(2)),
            Cross(Cell(5)),
            Cross(Cell(6)),
            Erase(Cell(5), extend = false),
            Erase(Cell(6), extend = true),
            Undo,
            Redo,
            Clear,
            Undo,
            AutoCross(on = true),
            Place(Cell(7)),
            Place(Cell(8)),
            Place(Cell(14)),
        )
        val facts = QueensReplay.factsOf(initial)

        val end = script.fold(initial) { session, move ->
            val next = session.step(move)
            assertNotSame("$move changed nothing", session, next)
            checkNotNull(next) { "$move was rejected" }
        }

        assertEquals(ReplayFacts(hints = 1, correct = 4, total = 4, solved = true), facts(end))
        assertSame(end, end.step(Undo))
        assertSame(end, end.step(Remove(Cell(1))))
    }

    // ------------------------------------------------------------------ facts

    private fun solvedSession(): QueensSession =
        initial.play(Place(Cell(1)), Place(Cell(7)), Place(Cell(8)), Place(Cell(14)))

    @Test
    fun `an empty board has no correct queen`() {
        assertEquals(ReplayFacts(hints = 0, correct = 0, total = 4, solved = false), factsOf(initial))
    }

    @Test
    fun `queens on solution cells count as correct`() {
        val partly = initial.play(Place(Cell(1)), Place(Cell(7)))

        assertEquals(ReplayFacts(hints = 0, correct = 2, total = 4, solved = false), factsOf(partly))
    }

    @Test
    fun `a queen off the solution is not correct`() {
        val wrong = initial.play(Place(Cell(1)), Place(Cell(11)))

        assertEquals(ReplayFacts(hints = 0, correct = 1, total = 4, solved = false), factsOf(wrong))
    }

    @Test
    fun `a cross on a solution cell is not a correct queen`() {
        assertEquals(0, factsOf(initial.play(Cross(Cell(1)))).correct)
    }

    @Test
    fun `a solved board has every queen correct`() {
        assertEquals(ReplayFacts(hints = 0, correct = 4, total = 4, solved = true), factsOf(solvedSession()))
    }

    /** Regions mirror the columns: 2, 4, 11, 13 is a second answer the stored solution does not name. */
    @Test
    fun `a board solved off the stored solution still has every queen correct`() {
        val twoAnswers = QueensPuzzle(4, (0 until 16).map { it % 4 }, 1, QueensTestSupport.n4Solution)
        val open = QueensSession(ref = null, puzzle = twoAnswers)
        val facts = QueensReplay.factsOf(open)

        val solved = open.play(Place(Cell(2)), Place(Cell(4)), Place(Cell(11)), Place(Cell(13)))

        assertEquals(ReplayFacts(hints = 0, correct = 4, total = 4, solved = true), facts(solved))
    }

    @Test
    fun `counted hints are reported`() {
        val hinted = initial.play(Hint, Place(Cell(1)), Hint)

        assertEquals(ReplayFacts(hints = 2, correct = 1, total = 4, solved = false), factsOf(hinted))
    }

    @Test
    fun `a puzzle without a stored solution is counted against the solver's`() {
        val unstored = QueensSession(ref = null, puzzle = QueensPuzzle(4, QueensTestSupport.n4Regions))
        val facts = QueensReplay.factsOf(unstored)

        assertEquals(1, facts(unstored.play(Place(Cell(1)), Place(Cell(11)))).correct)
        assertEquals(
            ReplayFacts(hints = 0, correct = 4, total = 4, solved = true),
            facts(unstored.play(Place(Cell(1)), Place(Cell(7)), Place(Cell(8)), Place(Cell(14)))),
        )
    }

    /** Regions mirror the columns, so the board has two answers and none to count against. */
    @Test
    fun `without one solution the queens in no violation count as correct`() {
        val open = QueensSession(ref = null, puzzle = QueensTestSupport.n4OpenPuzzle())
        val facts = QueensReplay.factsOf(open)

        assertEquals(1, facts(open.play(Place(Cell(1)), Place(Cell(5)), Place(Cell(11)))).correct)
        assertEquals(
            ReplayFacts(hints = 0, correct = 4, total = 4, solved = true),
            facts(open.play(Place(Cell(2)), Place(Cell(4)), Place(Cell(11)), Place(Cell(13)))),
        )
    }

    @Test
    fun `two fact functions of one puzzle are independent and agree`() {
        val first = QueensReplay.factsOf(initial)
        val second = QueensReplay.factsOf(initial)
        val boards = listOf(initial, initial.play(Hint, Place(Cell(1)), Place(Cell(11))), solvedSession())

        assertNotSame(first, second)
        assertEquals(boards.map(first), boards.map(second))
        assertFalse(first(initial).solved)
        assertTrue(second(solvedSession()).solved)
    }

    // ------------------------------------------------------------------ plugin

    @Test
    fun `the plugin exposes the adapter`() {
        assertSame(QueensReplay, queensPlugin.replay)
    }
}
