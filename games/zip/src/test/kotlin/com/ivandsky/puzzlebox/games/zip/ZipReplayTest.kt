package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.game.replay.ReplayFacts
import com.ivandsky.puzzlebox.game.replay.ReplayMove
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove.Extend
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove.Hint
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove.Redo
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove.Rejection
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove.Reset
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove.RetractTo
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove.Undo
import com.ivandsky.puzzlebox.games.zip.engine.ZipMove
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Zip replay adapter: every move kind, the persisted kind numbers and the board facts. */
class ZipReplayTest {

    private val at = 83_000L
    private val now = Instant.ofEpochMilli(at)

    /** 5×5 with one answer: the stored solution is the boustrophedon [ZipTestSupport.n5Solution]. */
    private val initial = ZipSession(ref = null, puzzle = ZipTestSupport.n5Puzzle())

    private fun wire(kind: Int, vararg args: Int) = ReplayMove(kind, args)

    private fun ZipSession.step(move: ZipReplayMove): ZipSession? = ZipReplay.step(this, move.toReplayMove(), at)

    private fun ZipSession.play(vararg moves: ZipReplayMove): ZipSession =
        moves.fold(this) { session, move -> checkNotNull(session.step(move)) { "$move was rejected" } }

    private fun ZipSession.extendedTo(vararg idx: Int): ZipSession = play(*idx.map { Extend(Cell(it)) }.toTypedArray())

    private fun assertRejected(session: ZipSession, vararg moves: ReplayMove) {
        for (move in moves) assertNull("$move", ZipReplay.step(session, move, at))
    }

    private fun factsOf(session: ZipSession): ReplayFacts = ZipReplay.factsOf(initial)(session)

    // ------------------------------------------------------------------ kinds

    @Test
    fun `extend grows the path by the cell`() {
        val grown = initial.extendedTo(0).step(Extend(Cell(1)))

        assertEquals(initial.extendedTo(0).withMove(ZipMove.Extend(Cell(1)), now), grown)
        assertEquals(path(0, 1), grown?.path)
    }

    @Test
    fun `extend on a finished trace changes nothing`() {
        val solved = solvedSession()

        assertSame(solved, solved.step(Extend(Cell(0))))
    }

    @Test
    fun `extend is rejected on a wrong arg count, a cell off the board or a step the rules refuse`() {
        val started = initial.extendedTo(0)

        assertRejected(started, wire(0), wire(0, 1, 2), wire(0, 25), wire(0, -1))
        assertRejected(started, wire(0, 12), wire(0, 0))
        assertRejected(initial, wire(0, 5))
    }

    @Test
    fun `retract cuts the path back to the cell`() {
        val grown = initial.extendedTo(0, 1, 2, 3)

        val cut = grown.step(RetractTo(Cell(1)))

        assertEquals(grown.withMove(ZipMove.RetractTo(Cell(1)), now), cut)
        assertEquals(path(0, 1), cut?.path)
        assertEquals(1, cut?.backtrackCount)
    }

    @Test
    fun `retract to the head or to a cell off the path changes nothing`() {
        val grown = initial.extendedTo(0, 1, 2)

        assertSame(grown, grown.step(RetractTo(Cell(2))))
        assertSame(grown, grown.step(RetractTo(Cell(24))))
    }

    @Test
    fun `retract is rejected on a wrong arg count or a cell off the board`() {
        val grown = initial.extendedTo(0, 1)

        assertRejected(grown, wire(1), wire(1, 1, 2), wire(1, 25), wire(1, -1))
    }

    @Test
    fun `reset empties the path`() {
        val grown = initial.extendedTo(0, 1, 2)

        val emptied = grown.step(Reset)

        assertEquals(grown.withMove(ZipMove.Reset, now), emptied)
        assertEquals(emptyList<Cell>(), emptied?.path)
    }

    @Test
    fun `reset on an empty path changes nothing`() {
        assertSame(initial, initial.step(Reset))
    }

    @Test
    fun `reset is rejected on args`() {
        assertRejected(initial, wire(2, 0))
    }

    @Test
    fun `undo takes back the newest edit and redo replays it`() {
        val grown = initial.extendedTo(0, 1, 2)

        val undone = grown.step(Undo)
        val redone = undone?.step(Redo)

        assertEquals(grown.withUndo(), undone)
        assertEquals(path(0, 1), undone?.path)
        assertEquals(grown.path, redone?.path)
    }

    @Test
    fun `undo and redo with nothing to take change nothing`() {
        assertSame(initial, initial.step(Undo))
        assertSame(initial, initial.step(Redo))
    }

    @Test
    fun `undo and redo are rejected on args`() {
        assertRejected(initial, wire(3, 0), wire(4, 0))
    }

    @Test
    fun `a drag replayed cell by cell undoes cell by cell and a drag back undoes whole`() {
        val dragged = initial.extendedTo(0, 1, 2, 3).play(RetractTo(Cell(1)))

        val undoneBack = dragged.play(Undo)
        assertEquals(path(0, 1, 2, 3), undoneBack.path)
        assertEquals(path(0, 1, 2), undoneBack.play(Undo).path)
    }

    @Test
    fun `hint counts one and leaves the path`() {
        val grown = initial.extendedTo(0, 1)

        val hinted = grown.step(Hint)

        assertEquals(grown.withHintApplied(), hinted)
        assertEquals(1, hinted?.hintsUsed)
        assertEquals(grown.path, hinted?.path)
        assertNotSame(grown, hinted)
    }

    @Test
    fun `hint on a finished trace changes nothing`() {
        val solved = solvedSession()

        assertSame(solved, solved.step(Hint))
    }

    @Test
    fun `hint is rejected on args`() {
        assertRejected(initial, wire(5, 0))
    }

    @Test
    fun `rejection counts a mistake as live play does`() {
        val grown = initial.extendedTo(0)

        val rejected = grown.step(Rejection)

        assertEquals(grown.withRejection(), rejected)
        assertEquals(1, rejected?.mistakeCount)
        assertEquals(grown.path, rejected?.path)
    }

    @Test
    fun `rejection on a finished trace changes nothing`() {
        val solved = solvedSession()

        assertSame(solved, solved.step(Rejection))
    }

    @Test
    fun `rejection is rejected on args`() {
        assertRejected(initial, wire(6, 0))
    }

    @Test
    fun `an unknown kind is rejected`() {
        assertRejected(initial, wire(7), wire(-1), wire(99, 0))
    }

    @Test
    fun `the step stamp is play time, not wall clock`() {
        val solved = solvedSession()

        assertEquals(now, solved.completedAt)
    }

    // ------------------------------------------------------------------ round trip

    @Test
    fun `every typed move survives the wire form`() {
        val moves = listOf(Extend(Cell(7)), RetractTo(Cell(3)), Reset, Undo, Redo, Hint, Rejection)

        for (move in moves) {
            assertEquals(move, ZipReplayMove.of(move.toReplayMove(), cellCount = 25))
        }
    }

    @Test
    fun `the kind numbers are the persisted format`() {
        assertEquals(wire(0, 7), Extend(Cell(7)).toReplayMove())
        assertEquals(wire(1, 3), RetractTo(Cell(3)).toReplayMove())
        assertEquals(wire(2), Reset.toReplayMove())
        assertEquals(wire(3), Undo.toReplayMove())
        assertEquals(wire(4), Redo.toReplayMove())
        assertEquals(wire(5), Hint.toReplayMove())
        assertEquals(wire(6), Rejection.toReplayMove())
    }

    // ------------------------------------------------------------------ scripted solve

    @Test
    fun `a scripted solve ends on a board the facts call solved`() {
        val facts = ZipReplay.factsOf(initial)
        val end = solvedSession()

        assertTrue(end.isComplete)
        assertEquals(ReplayFacts(hints = 0, correct = 25, total = 25, solved = true), facts(end))
    }

    @Test
    fun `a solve with a wrong turn, a retract and a hint folds to the same board`() {
        val facts = ZipReplay.factsOf(initial)
        val end = initial.extendedTo(0, 1, 2, 3)
            .play(RetractTo(Cell(1)), Hint)
            .extendedTo(*ZipTestSupport.n5Solution.drop(2).map { it.idx }.toIntArray())

        assertEquals(ReplayFacts(hints = 1, correct = 25, total = 25, solved = true), facts(end))
    }

    // ------------------------------------------------------------------ facts

    @Test
    fun `an empty board has nothing right`() {
        assertEquals(ReplayFacts(hints = 0, correct = 0, total = 25, solved = false), factsOf(initial))
    }

    @Test
    fun `a path on the solution counts its length`() {
        assertEquals(ReplayFacts(hints = 0, correct = 4, total = 25, solved = false), factsOf(initial.extendedTo(0, 1, 2, 3)))
    }

    @Test
    fun `a path off the solution counts only the shared prefix`() {
        val branchy = ZipSession(ref = null, puzzle = ZipTestSupport.n5BranchyPuzzle())
        val wrong = branchy.extendedTo(0, 1, 2, 3, 8)

        assertEquals(ReplayFacts(hints = 0, correct = 4, total = 25, solved = false), ZipReplay.factsOf(branchy)(wrong))
    }

    @Test
    fun `hints are counted`() {
        val hinted = initial.extendedTo(0).play(Hint, Hint)

        assertEquals(ReplayFacts(hints = 2, correct = 1, total = 25, solved = false), factsOf(hinted))
    }

    @Test
    fun `a board with no stored solution and several answers counts the path length`() {
        val open = ZipSession(ref = null, puzzle = ZipTestSupport.n5OpenPuzzle())
        val facts = ZipReplay.factsOf(open)

        assertEquals(ReplayFacts(hints = 0, correct = 3, total = 25, solved = false), facts(open.extendedTo(0, 1, 2)))
    }

    @Test
    fun `a board with no stored solution and one answer counts against the solver's`() {
        val unstored = ZipSession(
            ref = null,
            puzzle = ZipTestSupport.n5Puzzle().copy(solution = null),
        )
        val facts = ZipReplay.factsOf(unstored)

        assertEquals(4, facts(unstored.extendedTo(0, 1, 2, 3)).correct)
        assertEquals(25, facts(solvedSession(unstored)).correct)
    }

    @Test
    fun `facts called twice give independent functions with equal results`() {
        val first = ZipReplay.factsOf(initial)
        val second = ZipReplay.factsOf(initial)
        val board = initial.extendedTo(0, 1, 2)

        assertEquals(first(board), second(board))
        assertEquals(first(initial), second(initial))
    }

    @Test
    fun `the plugin exposes the adapter`() {
        assertNotNull(zipPlugin.replay)
        assertSame(ZipReplay, zipPlugin.replay)
    }

    private fun solvedSession(from: ZipSession = initial): ZipSession =
        from.extendedTo(*ZipTestSupport.n5Solution.map { it.idx }.toIntArray())
}
