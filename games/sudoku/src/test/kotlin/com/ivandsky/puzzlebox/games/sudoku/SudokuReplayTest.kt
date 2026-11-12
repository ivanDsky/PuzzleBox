package com.ivandsky.puzzlebox.games.sudoku

import com.ivandsky.puzzlebox.game.replay.ReplayFacts
import com.ivandsky.puzzlebox.game.replay.ReplayMove
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove.Clear
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove.Erase
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove.Hint
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove.HintReveal
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove.Place
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove.Redo
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove.ToggleNote
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove.Undo
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Sudoku replay adapter: every move kind, the persisted kind numbers and the board facts. */
class SudokuReplayTest {

    private val at = 83_000L
    private val now = Instant.ofEpochMilli(at)
    private val puzzle = KnownSolutions.peanut450
    private val initial = SudokuSession(ref = null, puzzle = puzzle)
    private val geometry = puzzle.geometry

    /** Open cells of #450, ascending; the first two (0 and 1) are row peers. */
    private val open = puzzle.givens.indices.filter { puzzle.givens[it] == 0 }
    private val given = puzzle.givens.indexOfFirst { it != 0 }
    private val a = open[0]
    private val b = open[1]
    private fun right(idx: Int) = puzzle.solution[idx]
    private fun wrong(idx: Int) = puzzle.solution[idx] % geometry.size + 1

    private fun wire(kind: Int, vararg args: Int) = ReplayMove(kind, args)

    private fun SudokuSession.step(move: SudokuReplayMove): SudokuSession? =
        SudokuReplay.step(this, move.toReplayMove(), at)

    private fun SudokuSession.play(vararg moves: SudokuReplayMove): SudokuSession =
        moves.fold(this) { session, move -> checkNotNull(session.step(move)) { "$move was rejected" } }

    private fun SudokuSession.direct(command: SudokuCommand?): SudokuSession =
        withCommand(requireNotNull(command), now)

    private fun assertRejected(session: SudokuSession, vararg moves: ReplayMove) {
        for (move in moves) assertNull("$move", SudokuReplay.step(session, move, at))
    }

    private fun factsOf(session: SudokuSession): ReplayFacts = SudokuReplay.factsOf(initial)(session)

    private fun solved(): SudokuSession = open.fold(initial) { session, idx -> session.play(Place(idx, right(idx))) }

    // ------------------------------------------------------------------ kinds

    @Test
    fun `place enters the digit through the live reducer`() {
        val placed = initial.step(Place(a, right(a)))

        assertEquals(initial.direct(SudokuMoves.place(initial.board, a, right(a))), placed)
        assertEquals(right(a), placed?.board?.digitAt(a))
    }

    @Test
    fun `place of the held digit or on a given changes nothing`() {
        val placed = initial.play(Place(a, right(a)))

        assertSame(placed, placed.step(Place(a, right(a))))
        assertSame(initial, initial.step(Place(given, wrong(given))))
    }

    @Test
    fun `place clears the digit's note from peers in the same unit`() {
        val noted = initial.play(ToggleNote(b, right(a)))
        val placed = noted.step(Place(a, right(a)))

        assertEquals(SudokuCell.Empty, placed?.board?.cells?.get(b))
        assertEquals(noted.board, placed?.withUndo()?.board)
    }

    @Test
    fun `toggle note flips the pencil note`() {
        val noted = initial.step(ToggleNote(a, 3))

        assertEquals(initial.direct(SudokuMoves.toggleNote(initial.board, a, 3)), noted)
        assertEquals(SudokuCell.Notes(1 shl 2), noted?.board?.cells?.get(a))
        assertEquals(SudokuCell.Empty, noted?.step(ToggleNote(a, 3))?.board?.cells?.get(a))
    }

    @Test
    fun `toggle note on an entry or a given changes nothing`() {
        val placed = initial.play(Place(a, right(a)))

        assertSame(placed, placed.step(ToggleNote(a, 3)))
        assertSame(initial, initial.step(ToggleNote(given, 3)))
    }

    @Test
    fun `erase removes the entry`() {
        val placed = initial.play(Place(a, right(a)))
        val erased = placed.step(Erase(a))

        assertEquals(placed.direct(SudokuMoves.erase(placed.board, a)), erased)
        assertEquals(SudokuCell.Empty, erased?.board?.cells?.get(a))
    }

    @Test
    fun `erase of an empty cell or a given changes nothing`() {
        assertSame(initial, initial.step(Erase(a)))
        assertSame(initial, initial.step(Erase(given)))
    }

    @Test
    fun `clear wipes every entry and note as one undo unit`() {
        val played = initial.play(Place(a, right(a)), ToggleNote(b, 2))
        val cleared = played.step(Clear)

        assertEquals(played.direct(SudokuMoves.clear(played.board)), cleared)
        assertEquals(initial.board, cleared?.board)
        assertEquals(played.board, cleared?.withUndo()?.board)
    }

    @Test
    fun `clear on a board with no entries changes nothing`() {
        assertSame(initial, initial.step(Clear))
    }

    @Test
    fun `undo and redo walk the history`() {
        val placed = initial.play(Place(a, right(a)))
        val undone = placed.step(Undo)

        assertEquals(placed.withUndo(), undone)
        assertEquals(initial.board, undone?.board)
        assertEquals(placed.board, undone?.step(Redo)?.board)
    }

    @Test
    fun `undo and redo with nothing to walk change nothing`() {
        val placed = initial.play(Place(a, right(a)))

        assertSame(initial, initial.step(Undo))
        assertSame(placed, placed.step(Redo))
    }

    @Test
    fun `hint reveal enters the digit as a revealed cell`() {
        val revealed = initial.step(HintReveal(a, right(a)))

        assertEquals(initial.direct(SudokuMoves.reveal(initial.board, a, right(a))), revealed)
        assertEquals(right(a), revealed?.board?.digitAt(a))
        assertTrue(revealed?.history?.done?.single() is SudokuCommand.HintReveal)
    }

    @Test
    fun `hint reveal of the held digit or on a given changes nothing`() {
        val revealed = initial.play(HintReveal(a, right(a)))

        assertSame(revealed, revealed.step(HintReveal(a, right(a))))
        assertSame(initial, initial.step(HintReveal(given, right(given))))
    }

    @Test
    fun `hint reveal of a digit that is not the solution's is rejected`() {
        assertRejected(initial, HintReveal(a, wrong(a)).toReplayMove())
    }

    @Test
    fun `hint counts one and touches no cell`() {
        val hinted = initial.step(Hint)

        assertEquals(initial.withHintUsed(), hinted)
        assertEquals(1, hinted?.hintsUsed)
        assertEquals(initial.board, hinted?.board)
    }

    @Test
    fun `a won session is final for every move`() {
        val won = solved()

        assertSame(won, won.step(Undo))
        assertSame(won, won.step(Hint))
        assertSame(won, won.step(Erase(a)))
        assertSame(won, won.step(Clear))
    }

    @Test
    fun `the win is stamped with the play time of the recorded move`() {
        assertEquals(now, solved().completedAt)
    }

    // ------------------------------------------------------------------ rejected

    @Test
    fun `an unknown kind is rejected`() {
        assertRejected(initial, wire(8), wire(-1), wire(Int.MAX_VALUE), wire(Int.MIN_VALUE, a, 1))
    }

    @Test
    fun `a wrong arg count is rejected`() {
        assertRejected(
            initial,
            wire(0), wire(0, a), wire(0, a, 1, 1),
            wire(1), wire(1, a), wire(1, a, 1, 1),
            wire(2), wire(2, a, 1),
            wire(3, 1), wire(4, 1), wire(5, 1),
            wire(6), wire(6, a), wire(6, a, 1, 1),
            wire(7, 1),
        )
    }

    @Test
    fun `an out of range cell or digit is rejected`() {
        val cells = geometry.cellCount
        val size = geometry.size
        assertRejected(
            initial,
            wire(0, -1, 1), wire(0, cells, 1), wire(0, a, 0), wire(0, a, size + 1), wire(0, a, -1),
            wire(1, -1, 1), wire(1, cells, 1), wire(1, a, 0), wire(1, a, size + 1),
            wire(2, -1), wire(2, cells), wire(2, Int.MAX_VALUE), wire(2, Int.MIN_VALUE),
            wire(6, -1, 1), wire(6, cells, 1), wire(6, a, 0), wire(6, a, size + 1),
        )
    }

    // ------------------------------------------------------------------ wire format

    @Test
    fun `every typed move round trips through its kind and args`() {
        val moves = listOf(
            Place(a, 3), ToggleNote(b, 4), Erase(a), Clear, Undo, Redo, HintReveal(b, 5), Hint,
        )

        for (move in moves) {
            assertEquals(move, SudokuReplayMove.of(move.toReplayMove(), geometry))
        }
    }

    @Test
    fun `the kind numbers are the persisted format`() {
        assertEquals(wire(0, 7, 3), Place(7, 3).toReplayMove())
        assertEquals(wire(1, 7, 3), ToggleNote(7, 3).toReplayMove())
        assertEquals(wire(2, 7), Erase(7).toReplayMove())
        assertEquals(wire(3), Clear.toReplayMove())
        assertEquals(wire(4), Undo.toReplayMove())
        assertEquals(wire(5), Redo.toReplayMove())
        assertEquals(wire(6, 7, 3), HintReveal(7, 3).toReplayMove())
        assertEquals(wire(7), Hint.toReplayMove())
    }

    // ------------------------------------------------------------------ facts

    @Test
    fun `facts of an empty board`() {
        assertEquals(ReplayFacts(hints = 0, correct = 0, total = open.size, solved = false), factsOf(initial))
    }

    @Test
    fun `facts of a partly right board count only the right digits`() {
        val partly = initial.play(Place(a, right(a)), Place(b, right(b)))

        assertEquals(ReplayFacts(0, 2, open.size, false), factsOf(partly))
    }

    @Test
    fun `facts do not count a wrong piece, a note or a given`() {
        val played = initial.play(Place(a, right(a)), Place(b, wrong(b)), ToggleNote(open[2], 2))

        assertEquals(ReplayFacts(0, 1, open.size, false), factsOf(played))
    }

    @Test
    fun `facts of a solved board`() {
        assertEquals(ReplayFacts(0, open.size, open.size, true), factsOf(solved()))
    }

    @Test
    fun `facts carry the counted hints and count a revealed digit as right`() {
        val hinted = initial.play(Hint, Hint, HintReveal(a, right(a)))

        assertEquals(ReplayFacts(2, 1, open.size, false), factsOf(hinted))
    }

    @Test
    fun `a scripted solve folded through step ends solved`() {
        val script = open.map { Place(it, right(it)).toReplayMove() }
        val end = script.fold(initial as SudokuSession?) { session, move -> session?.let { SudokuReplay.step(it, move, at) } }

        assertNotNull(end)
        assertTrue(end!!.isComplete)
        assertTrue(factsOf(end).solved)
        assertFalse(factsOf(initial).solved)
        assertNotEquals(factsOf(initial), factsOf(end))
    }

    @Test
    fun `factsOf called twice gives independent functions with equal results`() {
        val first = SudokuReplay.factsOf(initial)
        val second = SudokuReplay.factsOf(initial)
        val played = initial.play(Place(a, right(a)), Hint)

        assertEquals(first(played), second(played))
        assertEquals(first(initial), second(initial))
        assertEquals(first(played), first(played))
    }

    @Test
    fun `both plugins expose the replay adapter`() {
        assertSame(SudokuReplay, sudokuPlugin(SudokuGames.MINI).replay)
        assertSame(SudokuReplay, sudokuPlugin(SudokuGames.CLASSIC).replay)
    }
}
