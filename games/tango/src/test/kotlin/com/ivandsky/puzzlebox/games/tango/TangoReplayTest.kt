package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.replay.ReplayFacts
import com.ivandsky.puzzlebox.game.replay.ReplayMove
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove.Clear
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove.Hint
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove.Place
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove.Redo
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove.Rejection
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove.Undo
import com.ivandsky.puzzlebox.games.tango.engine.TangoMove
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Tango replay adapter: every move kind, the persisted kind numbers and the board facts. */
class TangoReplayTest {

    private val at = 83_000L
    private val now = Instant.ofEpochMilli(at)
    private val initial = TangoSession(ref = null, puzzle = TangoTestSupport.sample)
    private val solution = TangoTestSupport.solution
    private val playerCells = (0 until 36).filter { it !in TangoTestSupport.sample.givens }

    private fun wire(kind: Int, vararg args: Int) = ReplayMove(kind, args)

    private fun TangoSession.step(move: TangoReplayMove): TangoSession? =
        TangoReplay.step(this, move.toReplayMove(), at)

    private fun TangoSession.play(vararg moves: TangoReplayMove): TangoSession =
        moves.fold(this) { session, move -> checkNotNull(session.step(move)) { "$move was rejected" } }

    private fun assertRejected(session: TangoSession, vararg moves: ReplayMove) {
        for (move in moves) assertNull("$move", TangoReplay.step(session, move, at))
    }

    private fun factsOf(session: TangoSession): ReplayFacts = TangoReplay.factsOf(initial)(session)

    private fun right(idx: Int) = Place(idx, solution[idx])

    private fun solveAll(session: TangoSession = initial): TangoSession =
        session.play(*playerCells.map(::right).toTypedArray())

    // ------------------------------------------------------------------ kinds

    @Test
    fun `place sets the cell`() {
        val placed = initial.step(Place(2, TangoCell.MOON))

        assertEquals(initial.withMove(TangoMove(2, TangoCell.MOON), now), placed)
        assertEquals(mapOf(2 to TangoCell.MOON), placed?.placements)
    }

    @Test
    fun `place of the value already there changes nothing`() {
        val placed = initial.play(Place(2, TangoCell.MOON))

        assertSame(placed, placed.step(Place(2, TangoCell.MOON)))
        assertSame(initial, initial.step(Place(2, null)))
    }

    @Test
    fun `place with null empties the cell`() {
        val placed = initial.play(Place(2, TangoCell.MOON))

        assertEquals(emptyMap<Int, TangoCell>(), placed.step(Place(2, null))?.placements)
    }

    @Test
    fun `place that breaks a rule lands, and the rejection after it counts the mistake as live play does`() {
        val placed = initial.step(Place(2, TangoCell.SUN))
        val live = initial.withMove(TangoMove(2, TangoCell.SUN), now).withRejection()

        assertEquals(mapOf(2 to TangoCell.SUN), placed?.placements)
        assertEquals(live, initial.play(Place(2, TangoCell.SUN), Rejection))
        assertEquals(1, placed?.step(Rejection)?.mistakeCount)
    }

    @Test
    fun `rejection with args is refused`() {
        assertRejected(initial, wire(5, 0))
    }

    @Test
    fun `place on a given is refused`() {
        assertRejected(initial, Place(0, TangoCell.MOON).toReplayMove(), Place(0, null).toReplayMove())
    }

    @Test
    fun `place on a finished board changes nothing`() {
        val done = solveAll()

        assertSame(done, done.step(Place(playerCells.first(), null)))
    }

    @Test
    fun `clear wipes every player cell as one undo unit`() {
        val board = initial.play(Place(2, TangoCell.MOON), Place(3, TangoCell.SUN))
        val cleared = board.step(Clear)

        assertEquals(board.withCleared(), cleared)
        assertEquals(emptyMap<Int, TangoCell>(), cleared?.placements)
        assertEquals(board.placements, cleared?.withUndo()?.placements)
    }

    @Test
    fun `clear on an empty board changes nothing`() {
        assertSame(initial, initial.step(Clear))
    }

    @Test
    fun `undo and redo walk the history`() {
        val board = initial.play(Place(2, TangoCell.MOON), Place(3, TangoCell.SUN))
        val undone = board.step(Undo)

        assertEquals(board.withUndo(), undone)
        assertEquals(mapOf(2 to TangoCell.MOON), undone?.placements)
        assertEquals(board, undone?.step(Redo))
    }

    @Test
    fun `undo and redo with nothing to move change nothing`() {
        assertSame(initial, initial.step(Undo))
        assertSame(initial, initial.step(Redo))
        val board = initial.play(Place(2, TangoCell.MOON))
        assertSame(board, board.step(Redo))
    }

    @Test
    fun `hint counts one`() {
        val hinted = initial.step(Hint)

        assertEquals(1, hinted?.hintsUsed)
        assertEquals(2, hinted?.step(Hint)?.hintsUsed)
        assertEquals(initial.placements, hinted?.placements)
    }

    @Test
    fun `hint after its placement records both at one stamp`() {
        val board = initial.play(right(2), Hint)

        assertEquals(mapOf(2 to solution[2]), board.placements)
        assertEquals(1, board.hintsUsed)
    }

    @Test
    fun `hint on a finished board is refused`() {
        assertRejected(solveAll(), Hint.toReplayMove())
    }

    @Test
    fun `undo and redo on a finished board change nothing`() {
        val done = solveAll()

        assertSame(done, done.step(Undo))
        assertSame(done, done.step(Redo))
        assertSame(done, done.step(Clear))
    }

    @Test
    fun `the win is stamped with play time`() {
        assertEquals(now, solveAll().completedAt)
    }

    // ------------------------------------------------------------------ hostile and wire

    @Test
    fun `hostile moves are refused and never throw`() {
        assertRejected(
            initial,
            wire(-1), wire(Int.MAX_VALUE), wire(Int.MIN_VALUE),
            wire(0), wire(0, 2), wire(0, 2, 1, 1),
            wire(0, -1, 1), wire(0, 36, 1), wire(0, Int.MAX_VALUE, 1), wire(0, Int.MIN_VALUE, 1),
            wire(0, 2, -1), wire(0, 2, 3), wire(0, 2, Int.MAX_VALUE),
            wire(1, 0), wire(2, 0), wire(3, 0), wire(4, 0), wire(6),
        )
    }

    @Test
    fun `typed moves round trip through kind and args`() {
        val moves = listOf(
            Place(2, TangoCell.SUN), Place(35, TangoCell.MOON), Place(2, null), Clear, Undo, Redo, Hint, Rejection,
        )
        for (move in moves) assertEquals(move, TangoReplayMove.of(move.toReplayMove(), 36))
    }

    @Test
    fun `the kind numbers are the persisted format`() {
        assertEquals(wire(0, 2, 1), Place(2, TangoCell.SUN).toReplayMove())
        assertEquals(wire(0, 2, 2), Place(2, TangoCell.MOON).toReplayMove())
        assertEquals(wire(0, 2, 0), Place(2, null).toReplayMove())
        assertEquals(wire(1), Clear.toReplayMove())
        assertEquals(wire(2), Undo.toReplayMove())
        assertEquals(wire(3), Redo.toReplayMove())
        assertEquals(wire(4), Hint.toReplayMove())
        assertEquals(wire(5), Rejection.toReplayMove())
    }

    // ------------------------------------------------------------------ facts

    @Test
    fun `facts of an empty board`() {
        assertEquals(ReplayFacts(hints = 0, correct = 0, total = 26, solved = false), factsOf(initial))
    }

    @Test
    fun `facts count player cells that match the solution`() {
        val board = initial.play(right(2), right(3), Hint, Hint)

        assertEquals(ReplayFacts(hints = 2, correct = 2, total = 26, solved = false), factsOf(board))
    }

    @Test
    fun `facts do not count a wrong piece`() {
        val board = initial.play(right(2), Place(3, solution[3].other))

        assertEquals(1, factsOf(board).correct)
    }

    @Test
    fun `facts of a solved board`() {
        val done = solveAll().play()

        assertEquals(ReplayFacts(hints = 0, correct = 26, total = 26, solved = true), factsOf(done))
    }

    @Test
    fun `a solved board counts every cell correct even off the stored solution`() {
        val done = solveAll()
        val other = done.copy(placements = done.placements.mapValues { it.value.other })

        assertEquals(26, TangoReplay.factsOf(initial)(other).correct)
    }

    @Test
    fun `facts keep the hints of a solved board`() {
        val done = solveAll(initial.play(Hint))

        assertEquals(ReplayFacts(hints = 1, correct = 26, total = 26, solved = true), factsOf(done))
    }

    @Test
    fun `without a unique solution a cell counts while the rules find nothing wrong with it`() {
        val open = TangoSession(ref = null, puzzle = TangoPuzzle(VariantId("tango/n4"), 4, emptyMap(), emptyList()))
        val facts = TangoReplay.factsOf(open)

        assertEquals(ReplayFacts(0, 0, 16, false), facts(open))
        assertEquals(1, facts(open.play(Place(0, TangoCell.SUN))).correct)
        assertEquals(0, facts(open.play(Place(0, TangoCell.SUN), Place(1, TangoCell.SUN), Place(2, TangoCell.SUN))).correct)
    }

    @Test
    fun `factsOf twice gives independent functions with equal results`() {
        val a = TangoReplay.factsOf(initial)
        val b = TangoReplay.factsOf(initial)
        val board = initial.play(right(2), Hint)

        assertNotSame(a, b)
        assertEquals(a(board), b(board))
        assertEquals(a(initial), b(initial))
    }

    // ------------------------------------------------------------------ plugin

    @Test
    fun `the plugin exposes the adapter`() {
        assertTrue(tangoPlugin.replay === TangoReplay)
    }
}
