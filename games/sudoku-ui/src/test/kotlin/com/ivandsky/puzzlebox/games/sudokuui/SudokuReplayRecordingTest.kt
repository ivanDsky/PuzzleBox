package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.replay.MoveLog
import com.ivandsky.puzzlebox.game.replay.ReplayCodec
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.StampedMove
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.Restored
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.games.sudoku.SudokuCommand
import com.ivandsky.puzzlebox.games.sudoku.SudokuDefinition
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuMoves
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove.Clear
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove.Erase
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove.Hint
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove.HintReveal
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove.Place
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove.Redo
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove.ToggleNote
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove.Undo
import com.ivandsky.puzzlebox.games.sudoku.SudokuSamples
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuSessionCodec
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHint
import com.ivandsky.puzzlebox.games.sudoku.sudokuPlugin
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.SolveReplay
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Sudoku controller records what it applies: one fixed script per game runs through the real
 * controller on a fake clock, and the log, the replay and the completion are read back.
 */
class SudokuReplayRecordingTest {

    private var fakeNow: Instant = START

    /** One game: its definition, a puzzle to solve and the plugin a replay opens with. */
    private class Case(val definition: SudokuDefinition, val puzzle: SudokuPuzzle) {
        val ref = SudokuPuzzleRef(definition.id, PuzzleKey.Daily(LocalDate.of(2026, 11, 4), 450))
        val envelope = Puzzle(definition.id, puzzle.variant.id, SudokuCodec.VERSION, SudokuCodec.encode(puzzle))
        val plugin = sudokuPlugin(definition)
        val empties: List<Int> = puzzle.givens.indices.filter { puzzle.givens[it] == 0 }
        val given: Int = puzzle.givens.indices.first { puzzle.givens[it] != 0 }
        override fun toString() = definition.id.value
    }

    private val cases = listOf(
        Case(
            SudokuGames.MINI,
            SudokuPuzzle(
                SudokuGames.MINI.variant,
                grid("..3.5. .2.4.6 1..... .....2 2.4.1. .1.3.."),
                grid("463251 521436 142563 356142 234615 615324"),
            ),
        ),
        Case(SudokuGames.CLASSIC, SudokuSamples.classic),
    )

    private fun grid(text: String): List<Int> = text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    /** One play: the real controller with its log and what it handed to the play feature. */
    private inner class Play(val case: Case, val log: MoveLog, hintMode: HintMode = HintMode.Legacy) {
        val saved = ArrayList<ByteArray>()
        val won = ArrayList<CompletionRecord>()
        val controller = SudokuPlayController(
            definition = case.definition,
            env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), { saved += it }, { won += it }, log),
            highlights = InMemorySudokuHighlightSettings(),
            now = { fakeNow },
            hintMode = hintMode,
        )
        val board: SudokuBoardState get() = controller.board!!
        val session: SudokuSession get() = board.session

        /** The moves the script made, with the play time each landed at. */
        val moves = ArrayList<StampedMove>()

        /** Runs [script] from play time [fromMs]; [afterEach] sees the play right after every input. */
        fun run(script: List<Input>, fromMs: Long = 0L, afterEach: (Input) -> Unit = {}) {
            var playMs = fromMs
            for (input in script) {
                fakeNow = fakeNow.plusMillis(input.awayMs + input.atMs - playMs)
                playMs = input.atMs
                val before = session
                input.act(this)
                input.move(before, this)?.let { moves += StampedMove(input.atMs, it.toReplayMove()) }
                afterEach(input)
            }
        }
    }

    /**
     * One scripted input. It lands at play time [atMs], after [awayMs] more of wall time that is not
     * play. [move] is what the log must hold for it, given the session before and the play after (null =
     * nothing). [today] is the reducer call the controller made for it before it recorded anything,
     * with the wall-clock instant it passed.
     */
    private class Input(
        val atMs: Long,
        val move: (before: SudokuSession, play: Play) -> SudokuReplayMove?,
        val awayMs: Long = 0L,
        val today: SudokuSession.(Instant, Play) -> SudokuSession = { _, _ -> this },
        val act: Play.() -> Unit,
    )

    private fun fresh(case: Case, log: MoveLog = MoveLog.off()): Play {
        fakeNow = START
        val play = Play(case, log)
        assertEquals(Adopt.Adopted, play.controller.adoptToday(case.envelope, case.ref))
        return play
    }

    /** A killed play comes back: a new controller over the saved [log] snapshot and the saved session [bytes]. */
    private fun restored(case: Case, log: MoveLog, bytes: ByteArray): Play {
        val play = Play(case, log)
        assertEquals(Restored(case.ref), play.controller.adoptRestored(bytes))
        return play
    }

    private fun Play.liveAfterEach(script: List<Input>, fromMs: Long = 0L): Map<Long, SudokuSession> {
        val live = linkedMapOf(fromMs to session)
        run(script, fromMs) { live[it.atMs] = session }
        return live
    }

    /**
     * What a replay reproduces of a live session: board, undo history and hint count. Not the
     * record identity, the notes setting, the banked clock or the wall-clock moment of the win.
     */
    private fun SudokuSession.clockFree(): SudokuSession =
        copy(ref = null, elapsedMs = 0L, notesMode = false, completedAt = completedAt?.let { Instant.EPOCH })

    private fun Play.sealed(): SolveReplay =
        checkNotNull(log.seal(ReplayOutcome.Solved, won.single().time)) { "the play sealed no replay" }

    private fun Play.recorded(): List<StampedMove> =
        checkNotNull(log.snapshot()?.let(ReplayCodec::decode)?.getOrNull()) { "the play has no readable log" }.moves

    private fun timelineOf(case: Case, sealed: SolveReplay): Timeline =
        checkNotNull(case.plugin.openTimeline(case.envelope, sealed)) { "the sealed replay does not open" }

    private fun Timeline.sessionAt(atMs: Long): SudokuSession = at(atMs).session as SudokuSession

    /** S2 for one play: every live session is the replay's frame at its play time, and the replay ends solved. */
    private fun assertReplays(case: Case, live: Map<Long, SudokuSession>, sealed: SolveReplay) {
        val timeline = timelineOf(case, sealed)
        for ((atMs, session) in live) {
            assertEquals("${case.definition.id.value} frame at $atMs ms", session.clockFree(), timeline.sessionAt(atMs).clockFree())
        }
        val end = timeline.at(timeline.endMs)
        assertTrue(end.ended)
        assertTrue(end.facts.solved)
        val board = (end.session as SudokuSession).board
        assertEquals(case.puzzle.solution, case.puzzle.solution.indices.map(board::digitAt))
    }

    //region scripts

    private fun SudokuSession.command(command: SudokuCommand?, now: Instant): SudokuSession =
        if (command == null) this else withCommand(command, now)

    /** A command that wins banks the whole running window: [atMs] of play time. */
    private fun SudokuSession.applied(command: SudokuCommand?, now: Instant, atMs: Long): SudokuSession {
        val next = command(command, now)
        return if (next.isComplete && !isComplete) next.copy(elapsedMs = atMs) else next
    }

    private fun input(atMs: Long, move: SudokuReplayMove?, today: SudokuSession.(Instant, Play) -> SudokuSession, act: Play.() -> Unit) =
        Input(atMs, { _, _ -> move }, today = today, act = act)

    private fun ignored(atMs: Long, act: Play.() -> Unit) = Input(atMs, { _, _ -> null }, act = act)

    private fun background(atMs: Long) =
        Input(atMs, { _, _ -> null }, today = { _, _ -> copy(elapsedMs = atMs) }) { controller.onBackground() }

    private fun foreground(atMs: Long, awayMs: Long) = Input(atMs, { _, _ -> null }, awayMs = awayMs) { controller.onForeground() }

    private fun notesToggle(atMs: Long) =
        input(atMs, null, { _, _ -> withNotesMode(!notesMode) }) { board.toggleNotesMode() }

    private fun place(atMs: Long, idx: Int, digit: Int) = input(
        atMs, Place(idx, digit),
        { now, _ -> applied(SudokuMoves.place(board, idx, digit), now, atMs) },
    ) {
        board.select(idx)
        board.input(digit)
    }

    private fun Case.script(): List<Input> {
        val (e0, e1, e2) = empties
        val sol = puzzle.solution
        val wrong = sol[e0] % puzzle.variant.geometry.size + 1
        val hinted: SudokuSession.(Play) -> Pair<Int, Int>? = { play ->
            when (val h = play.board.hint) {
                is SudokuHint.Place -> h.cell to h.digit
                is SudokuHint.Reveal -> h.cell to h.digit
                else -> null
            }
        }
        return openingHalf(e0, e1, e2, wrong, sol[e0], sol[e2], hinted) + pause() + closingHalf(sol)
    }

    /** Every kind of board action and the input nothing records. Ends at 2.4 s with one counted hint and one reveal. */
    private fun Case.openingHalf(
        e0: Int,
        e1: Int,
        e2: Int,
        wrong: Int,
        right0: Int,
        right2: Int,
        hinted: SudokuSession.(Play) -> Pair<Int, Int>?,
    ): List<Input> = listOf(
        place(1_000L, e0, wrong),
        place(1_100L, e0, right0),
        place(1_200L, e2, right2),
        input(1_300L, Erase(e2), { now, _ -> applied(SudokuMoves.erase(board, e2), now, 1_300L) }) { board.erase() },
        input(1_400L, Undo, { _, _ -> withUndo() }) { controller.undo() },
        input(1_500L, Redo, { _, _ -> withRedo() }) { controller.redo() },
        ignored(1_600L) { controller.redo() }, // nothing left to redo
        ignored(1_700L) { // refused: a Given
            board.select(given)
            board.input(1)
        },
        notesToggle(1_750L), // a setting, never a move
        input(1_800L, ToggleNote(e1, 1), { now, _ -> applied(SudokuMoves.toggleNote(board, e1, 1), now, 1_800L) }) {
            board.select(e1)
            board.input(1)
        },
        notesToggle(1_850L),
        input(1_900L, Erase(e1), { now, _ -> applied(SudokuMoves.erase(board, e1), now, 1_900L) }) { board.erase() },
        ignored(1_950L) { board.erase() }, // nothing left to erase
        input(2_000L, Clear, { now, _ -> applied(SudokuMoves.clear(board), now, 2_000L) }) { board.clear() },
        input(2_100L, Undo, { _, _ -> withUndo() }) { controller.undo() },
        input(2_200L, Hint, { _, _ -> withHintUsed() }) { controller.requestHint() },
        Input(
            2_300L,
            { before, play -> play.session.takeIf { it.board != before.board }?.let { hinted(before, play) }?.let { (cell, digit) -> HintReveal(cell, digit) } },
            today = { now, play -> hinted(play)?.let { (cell, digit) -> applied(SudokuMoves.reveal(board, cell, digit), now, 2_300L) } ?: this },
        ) { controller.requestHint() },
    )

    /** A minute away from the app at play time 2.4 s. */
    private fun pause(): List<Input> = listOf(background(2_400L), foreground(2_400L, awayMs = 60_000L))

    /** Fills every cell that is still wrong, one per 100 ms; the last one wins. */
    private fun Case.closingHalf(sol: List<Int>): List<Input> = empties.mapIndexed { i, idx ->
        val atMs = 3_000L + 100L * i
        Input(
            atMs,
            { before, _ -> Place(idx, sol[idx]).takeIf { before.board.digitAt(idx) != sol[idx] } },
            today = { now, _ ->
                if (board.digitAt(idx) == sol[idx]) this else applied(SudokuMoves.place(board, idx, sol[idx]), now, atMs)
            },
        ) {
            if (board.session.board.digitAt(idx) != sol[idx]) {
                board.select(idx)
                board.input(sol[idx])
            }
        }
    }

    /** The script's first half, up to and including the background at 2.4 s. */
    private fun firstHalf(all: List<Input>): List<Input> = all.subList(0, all.indexOfFirst { it.atMs == OPENING_END_MS } + 1)

    /** The rest, after the foreground a restore makes for itself. */
    private fun secondHalf(all: List<Input>): List<Input> = all.filter { it.atMs > OPENING_END_MS }

    //endregion

    private fun lastStamp(moves: List<StampedMove>) = moves.last().atMs

    /**
     * Characterization: written against the controller that called the session reducers itself,
     * and green there. Routing every change through the move log must not shift one session.
     */
    @Test
    fun `the controller stores what today's reducer calls give, input for input`() {
        for (case in cases) {
            for (log in listOf(MoveLog.off(), MoveLog.fresh(case.definition.id))) {
                val play = fresh(case, log)
                var expected = SudokuSession(ref = case.ref, puzzle = case.puzzle)
                assertEquals(expected, play.session)

                play.run(case.script()) { input ->
                    expected = input.today(expected, fakeNow, play)
                    assertEquals("$case after the input at ${input.atMs} ms", expected, play.session)
                }

                assertTrue("$case solves", play.session.isComplete)
                assertEquals(case.puzzle.solution, case.puzzle.solution.indices.map(play.session.board::digitAt))
                val last = case.script().last().atMs
                assertEquals(START.plusMillis(60_000L + last), play.session.completedAt)
                assertEquals(last, play.won.single().time)
                assertEquals(1, play.won.single().hints)
            }
        }
    }

    @Test
    fun `S2 the replay shows the live session after every input and ends on the solved board`() {
        for (case in cases) {
            val play = fresh(case, MoveLog.fresh(case.definition.id))
            val live = play.liveAfterEach(case.script())

            assertReplays(case, live, play.sealed())
        }
    }

    @Test
    fun `S3 save, kill, restore, win seals the bytes of the uninterrupted replay`() {
        for (case in cases) {
            val script = case.script()
            val whole = fresh(case, MoveLog.fresh(case.definition.id))
            whole.run(script)

            val killed = fresh(case, MoveLog.fresh(case.definition.id))
            killed.run(firstHalf(script))
            fakeNow = fakeNow.plusSeconds(3_600L) // an hour dead: never play time
            val back = restored(case, MoveLog.restored(case.definition.id, killed.log.snapshot()), killed.saved.last())
            val live = back.liveAfterEach(secondHalf(script), fromMs = OPENING_END_MS)

            assertArrayEquals("$case", whole.sealed().toByteArray(), back.sealed().toByteArray())
            assertEquals(whole.won.single().time, back.won.single().time)
            assertEquals(1, back.won.single().hints)
            assertReplays(case, live, back.sealed())
        }
    }

    @Test
    fun `S4 every stamp is the play time its input landed at, never decreasing, the last within the recorded time`() {
        for (case in cases) {
            val play = fresh(case, MoveLog.fresh(case.definition.id))
            play.run(case.script())

            val recorded = play.recorded()
            assertEquals("$case", play.moves, recorded)
            assertTrue(recorded.map { it.atMs }.containsAll(listOf(1_000L, 1_100L, 1_200L, 2_200L, 2_300L)))
            assertTrue(recorded.zipWithNext().all { (earlier, later) -> earlier.atMs <= later.atMs })
            assertTrue(lastStamp(recorded) <= play.won.single().time)
            assertEquals(lastStamp(recorded), play.won.single().time)
        }
    }

    @Test
    fun `S5 recording changes neither the completion record nor one saved session`() {
        for (case in cases) {
            val off = fresh(case, MoveLog.off())
            off.run(case.script())
            val on = fresh(case, MoveLog.fresh(case.definition.id))
            on.run(case.script())

            assertNull(off.log.snapshot())
            assertEquals(on.moves, on.recorded())
            assertEquals(off.won, on.won)
            assertEquals(off.saved.size, on.saved.size)
            off.saved.zip(on.saved).forEachIndexed { i, (a, b) -> assertArrayEquals("$case save $i", a, b) }
            assertArrayEquals(off.saved.last(), SudokuSessionCodec.serialize(on.session))
        }
    }

    /** A counted Stepwise hint is one Hint move, and the Spotlight it leaves is not a move. */
    @Test
    fun `a counted stepwise hint records one Hint move and a free re-show records none`() {
        val case = cases.first()
        fakeNow = START
        val play = Play(case, MoveLog.fresh(case.definition.id), HintMode.Stepwise(Dispatchers.Unconfined))
        play.controller.adoptToday(case.envelope, case.ref)

        fakeNow = fakeNow.plusMillis(500L)
        play.controller.requestHint()
        val counted = play.session.hintsUsed
        fakeNow = fakeNow.plusMillis(500L)
        play.controller.requestHint()

        assertEquals(1, counted)
        assertEquals(1, play.session.hintsUsed)
        assertEquals(listOf(StampedMove(500L, Hint.toReplayMove())), play.recorded())
    }

    private companion object {
        val START: Instant = Instant.parse("2026-11-02T00:00:00Z")
        const val OPENING_END_MS = 2_400L
    }
}
