package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.games.zip.engine.ZipValidation
import com.ivandsky.puzzlebox.games.zip.engine.ZipVariants
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ticket 13 state contract: load/degrade, move validation, hints, win flow, restore. */
class ZipPlayStateTest {

    private val day: LocalDate = LocalDate.of(2026, 10, 31)
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun state(
        daily: FakeDailyRepository,
        completions: CompletionRepository = FakeCompletionRepository(),
        now: () -> Instant = Instant::now,
    ) = ZipPlayState(
        daily = daily,
        completions = completions,
        scope = scope,
        now = now,
        today = { day },
    )

    private fun freshDaily(history: Boolean = true) = PlayTestSupport.daily(
        history = if (history) listOf(PlayTestSupport.entry(day)) else emptyList(),
    )

    /** Loads the fixture synchronously (Unconfined scope) and returns the ready state. */
    private fun readyState(
        daily: FakeDailyRepository = freshDaily(),
        completions: CompletionRepository = FakeCompletionRepository(),
        now: () -> Instant = Instant::now,
    ): ZipPlayState = state(daily, completions, now).also {
        it.load()
        check(it.screen is ZipScreen.Ready) { "fixture load did not reach Ready: ${it.screen}" }
    }

    private fun requireReady(s: ZipPlayState): ZipScreen.Ready = s.screen as ZipScreen.Ready

    private fun playSolution(s: ZipPlayState) {
        PlayTestSupport.solution.forEach { cell ->
            s.onDragEvent(DragEvent.Extend(GridCell(cell.idx / 5, cell.idx % 5)))
        }
    }

    private fun recordsOf(repo: CompletionRepository): List<CompletionRecord> =
        runBlocking { repo.observeRecords().first() }

    //region load / degrade states (A7/A8)

    @Test
    fun `fresh fetch adopts the ready board with today's identity`() {
        val daily = freshDaily()
        val s = state(daily)
        assertEquals(ZipScreen.Loading, s.screen)
        s.load()

        val ready = requireReady(s)
        assertNull(ready.degraded)
        assertEquals(ZipPuzzleRef(day, 592), s.session?.ref)
        assertEquals(emptyList<Cell>(), s.session?.path)
        assertEquals(ZipRendererModel.Status.IDLE, s.rendererModel()?.status)
    }

    @Test
    fun `cached degrade plays the saved puzzle with the banner flag`() {
        val daily = PlayTestSupport.daily(
            result = DailyToday.Cached(PlayTestSupport.envelope(), FetchError.Unavailable("offline")),
            history = listOf(PlayTestSupport.entry(day)),
        )
        val s = state(daily)
        s.load()

        assertEquals(FetchError.Unavailable("offline"), requireReady(s).degraded)
        assertTrue(degradeBannerMessage(requireReady(s).degraded!!).isNotEmpty())
        assertEquals(ZipPuzzleRef(day, 592), s.session?.ref)
    }

    @Test
    fun `unavailable with nothing captured is a retryable error and retry refetches`() {
        val daily = PlayTestSupport.daily(result = DailyToday.Unavailable(FetchError.Unavailable("no net")))
        val s = state(daily)
        s.load()
        assertTrue(s.screen is ZipScreen.Unavailable)
        assertNull(s.session)
        assertTrue(unavailableMessage(FetchError.Unavailable(null)).isNotEmpty())

        s.load()
        assertEquals(2, daily.todayCalls(ZipDefinition.id))
    }

    @Test
    fun `untrustable payload is never playable (A16)`() {
        val garbage = Puzzle(
            gameId = ZipDefinition.id,
            variant = ZipVariants.byId(VariantId("zip/n5"))!!.id,
            formatVersion = 1,
            payload = byteArrayOf(99),
        )
        val s = state(PlayTestSupport.daily(result = DailyToday.Fresh(garbage)))
        s.load()
        assertTrue(s.screen is ZipScreen.Unavailable)
        assertNull(s.session)
    }

    //endregion

    //region moves (F2 §7 authoritative validation)

    @Test
    fun `valid drag extends the trace and starts the timer`() {
        val s = readyState()
        s.onDragEvent(DragEvent.Extend(GridCell(0, 0)))
        s.onDragEvent(DragEvent.Extend(GridCell(0, 1)))

        assertEquals(listOf(Cell(0), Cell(1)), s.session?.path)
        assertEquals(true, s.session?.timerStarted)
        assertNull(s.hintCell)
    }

    @Test
    fun `an invalid extend is one mistake and one rejection, path untouched`() {
        val s = readyState()
        s.onDragEvent(DragEvent.Extend(GridCell(2, 2))) // not waypoint 1 → WrongStart

        assertEquals(1, s.session?.mistakeCount)
        assertEquals(emptyList<Cell>(), s.session?.path)
        assertTrue(s.rejection?.reason is ZipValidation.Reason.WrongStart)
    }

    @Test
    fun `retract event trims and counts a backtrack`() {
        val s = readyState()
        listOf(0, 1, 2).forEach { s.onDragEvent(DragEvent.Extend(GridCell(it / 5, it % 5))) }
        s.onDragEvent(DragEvent.Retract(index = 0))

        assertEquals(listOf(Cell(0)), s.session?.path)
        assertEquals(1, s.session?.backtrackCount)
    }

    @Test
    fun `clear event empties the trace to the start state and counts one backtrack`() {
        val s = readyState()
        listOf(0, 1, 2).forEach { s.onDragEvent(DragEvent.Extend(GridCell(it / 5, it % 5))) }
        s.onDragEvent(DragEvent.Clear) // tap on the first traced cell (R1)

        assertEquals(emptyList<Cell>(), s.session?.path)
        assertEquals(1, s.session?.backtrackCount)
        assertEquals(ZipRendererModel.Status.IDLE, s.rendererModel()?.status)

        // The emptied trace redraws from the start like a fresh session.
        s.onDragEvent(DragEvent.Extend(GridCell(0, 0)))
        assertEquals(listOf(Cell(0)), s.session?.path)
    }

    //region undo / redo (PlayUndo over the session's command history)

    private fun extend(s: ZipPlayState, vararg idx: Int) =
        idx.forEach { s.onDragEvent(DragEvent.Extend(GridCell(it / 5, it % 5))) }

    @Test
    fun `undo and redo step the trace, clear the hint arrow and persist each step`() {
        val saved = mutableListOf<com.ivandsky.puzzlebox.games.zip.ZipSession?>()
        val s = ZipPlayState(
            daily = freshDaily(),
            completions = FakeCompletionRepository(),
            scope = scope,
            today = { day },
            onSessionChanged = { saved += it },
        ).also { it.load() }
        extend(s, 0, 1, 2)
        assertTrue(s.canUndo)
        assertFalse(s.canRedo)

        s.hint() // places cell 3 with an arrow
        assertTrue(s.hintArrow != null)
        val beforeUndo = saved.size
        s.undo()
        assertNull(s.hintArrow)
        assertEquals(PlayTestSupport.solution.take(3), s.session?.path)
        assertTrue(s.canRedo)
        assertEquals(s.session, saved.last())

        s.redo()
        assertEquals(PlayTestSupport.solution.take(4), s.session?.path)
        assertFalse(s.canRedo)
        assertEquals(beforeUndo + 2, saved.size)

        // nothing to redo: no state change, nothing persisted
        s.redo()
        assertEquals(beforeUndo + 2, saved.size)
    }

    @Test
    fun `undo leaves hints, mistakes, backtracks and the timer as played`() {
        val tick = AtomicLong(1_000)
        val s = readyState(now = { Instant.ofEpochMilli(tick.getAndAdd(100)) })
        extend(s, 0, 1, 2)
        s.onDragEvent(DragEvent.Extend(GridCell(4, 4))) // rejected: a mistake
        s.onDragEvent(DragEvent.Retract(index = 0)) // a backtrack
        s.hint()
        val before = s.session!!

        s.undo()
        s.undo()

        val after = s.session!!
        assertNotEquals(before.path, after.path)
        assertEquals(before.hintsUsed, after.hintsUsed)
        assertEquals(before.mistakeCount, after.mistakeCount)
        assertEquals(before.backtrackCount, after.backtrackCount)
        assertEquals(before.elapsedMs, after.elapsedMs)
        assertEquals(before.runningSince, after.runningSince)
        assertEquals(before.timerStarted, after.timerStarted)
    }

    @Test
    fun `a new move after an undo forks the timeline but a rejected drop does not`() {
        val s = readyState()
        extend(s, 0, 1, 2)
        s.undo()
        assertTrue(s.canRedo)

        s.onDragEvent(DragEvent.Extend(GridCell(4, 4))) // rejected: not a move
        assertTrue(s.canRedo)

        extend(s, 2) // an accepted move: the old future is gone
        assertEquals(PlayTestSupport.solution.take(3), s.session?.path)
        assertFalse(s.canRedo)
    }

    @Test
    fun `a finished play is frozen against undo and redo`() {
        val s = readyState()
        playSolution(s)
        assertTrue(s.session!!.isComplete)
        assertFalse(s.canUndo)
        assertFalse(s.canRedo)
        val solved = s.session
        s.undo()
        s.redo()
        assertEquals(solved, s.session)
    }

    @Test
    fun `history survives persist and restore`() {
        val s = readyState()
        extend(s, 0, 1, 2)
        s.undo()

        val restored = ZipPlayState(
            daily = freshDaily(),
            completions = FakeCompletionRepository(),
            scope = scope,
            today = { day },
        )
        restored.adoptRestored(s.persist()!!)

        assertTrue(restored.canUndo)
        assertTrue(restored.canRedo)
        restored.redo()
        assertEquals(PlayTestSupport.solution.take(3), restored.session?.path)
        restored.undo()
        restored.undo()
        assertEquals(PlayTestSupport.solution.take(1), restored.session?.path)
    }

    @Test
    fun `result label names the puzzle number when known`() {
        assertEquals("Zip · #592", readyState().resultLabel)
        assertEquals("Zip", readyState(daily = freshDaily(history = false)).resultLabel)
    }

    //endregion

    //region hints (A3 / F2 §10)

    @Test
    fun `hint on a fresh board reveals one step and counts one hint`() {
        val s = readyState()
        s.hint()

        assertEquals(1, s.session?.hintsUsed)
        // Empty trace: the smallest help is the start cell itself — auto-placed (ticket
        // 28), no arrow because a lone cell has no segment direction.
        assertEquals(listOf(PlayTestSupport.solution[0]), s.session?.path)
        assertEquals(PlayTestSupport.solution[0].idx, s.hintCell)
        assertNull(s.hintArrow)
    }

    @Test
    fun `hint on a dead trace trims free, autoplaces the step, counts one hint`() {
        val branchy = PlayTestSupport.daily(
            result = DailyToday.Fresh(PlayTestSupport.envelope(PlayTestSupport.branchyPuzzle())),
            history = listOf(PlayTestSupport.entry(day)),
        )
        val s = readyState(daily = branchy)
        PlayTestSupport.deadTrace().forEach { s.onDragEvent(DragEvent.Extend(GridCell(it.idx / 5, it.idx % 5))) }
        assertEquals(5, s.session?.path?.size)

        s.hint()

        assertEquals(1, s.session?.hintsUsed)
        // Trimmed to the longest solvable prefix, then the ONE counted hint AUTO-PLACES
        // the next solution segment (ticket 28) with its arrow.
        assertEquals(PlayTestSupport.solution.take(5), s.session?.path)
        assertEquals(PlayTestSupport.solution[4].idx, s.hintCell)
        assertEquals(
            HintArrow(
                headIdx = PlayTestSupport.solution[4].idx,
                tailIdx = PlayTestSupport.solution[3].idx,
            ),
            s.hintArrow,
        )
    }

    @Test
    fun `an accepted move clears the hint highlight and the arrow`() {
        val s = readyState()
        s.hint() // auto-places the start
        s.onDragEvent(DragEvent.Extend(GridCell(0, 1))) // solution[1], accepted

        assertNull(s.hintCell)
        assertNull(s.hintArrow)
    }

    @Test
    fun `hint arrow dies on any next input`() {
        val s = readyState()
        s.onDragEvent(DragEvent.Extend(GridCell(0, 0)))
        s.hint()
        assertEquals(HintArrow(headIdx = 1, tailIdx = 0), s.hintArrow)

        // Even a rejected input (a tap that cannot extend) kills the arrow: next input.
        s.onDragEvent(DragEvent.Extend(GridCell(4, 4)))
        assertNull(s.hintArrow)
    }

    @Test
    fun `hint cooldown no-ops rapid presses and telemetry records placements`() {
        var clockMs = 0L
        val s = readyState(now = { Instant.ofEpochMilli(clockMs) })
        s.hint() // places the start (empty trace, no arrow)
        assertEquals(1, s.session?.hintsUsed)
        assertEquals(
            listOf(HintEvent(atMs = 0L, fromIdx = null, placedIdx = PlayTestSupport.solution[0].idx)),
            s.hintHistory,
        )

        s.hint() // inside the 5s cooldown: a no-op — not counted, not recorded
        assertEquals(1, s.session?.hintsUsed)
        assertEquals(1, s.hintHistory.size)

        clockMs += 5_000L
        s.hint() // cooldown expired: places the next solution segment
        assertEquals(2, s.session?.hintsUsed)
        assertEquals(
            listOf(
                HintEvent(atMs = 5_000L, fromIdx = 0, placedIdx = PlayTestSupport.solution[1].idx),
            ),
            s.hintHistory.drop(1),
        )
    }

    @Test
    fun `replay resets the hint arrow`() {
        val s = readyState()
        s.onDragEvent(DragEvent.Extend(GridCell(0, 0)))
        s.hint()
        assertEquals(HintArrow(headIdx = 1, tailIdx = 0), s.hintArrow)

        s.replay()
        assertNull(s.hintArrow)
        assertNull(s.hintCell)
    }

    //endregion

    //region win flow (A4 / F2 §7)

    @Test
    fun `completing the board wins once and writes exactly one record`() {
        val repo = FakeCompletionRepository()
        val tick = AtomicLong(0)
        val s = readyState(completions = repo, now = { Instant.ofEpochMilli(tick.getAndAdd(100)) })

        playSolution(s)

        assertTrue(s.session?.isComplete == true)
        assertTrue((s.session?.elapsedMs ?: 0L) > 0)
        assertEquals(1, recordsOf(repo).size)
        assertFalse(s.recordWriteFailed)
        assertEquals(true, s.recordBadge)
        // The completed session accepts no further moves (one play-through, one completion).
        s.onDragEvent(DragEvent.Extend(GridCell(0, 0)))
        assertEquals(1, recordsOf(repo).size)
    }

    @Test
    fun `replay after a win emits again and the badge shows not-a-record`() {
        val repo = FakeCompletionRepository()
        val s = readyState(completions = repo)
        playSolution(s)
        s.replay()
        assertNull(s.session?.completedAt)
        assertNull(s.recordBadge)

        playSolution(s)

        assertEquals(2, recordsOf(repo).size)
        assertEquals(false, s.recordBadge) // baseline had a record: this play is a replay (A4)
    }

    @Test
    fun `a failed record write surfaces retryable, never blocks the win`() {
        val flaky = FakeCompletionRepository().apply { fail = true }
        val s = readyState(completions = flaky)
        playSolution(s)

        assertTrue(s.session?.isComplete == true) // overlay shown regardless (F2 §7)
        assertTrue(s.recordWriteFailed)
        assertEquals(0, recordsOf(flaky).size)

        flaky.fail = false
        s.retryRecordWrite()

        assertEquals(1, recordsOf(flaky).size)
        assertFalse(s.recordWriteFailed)
        assertEquals(true, s.recordBadge)
    }

    //endregion

    //region timer (F2 §2)

    @Test
    fun `background folds the running timer`() {
        val tick = AtomicLong(1_000)
        val s = readyState(now = { Instant.ofEpochMilli(tick.getAndAdd(100)) })
        s.onDragEvent(DragEvent.Extend(GridCell(0, 0)))

        s.onBackground()
        val banked = s.session?.elapsedMs ?: 0
        assertNotEquals(0L, banked)
        assertEquals(banked, s.session?.elapsedMs) // stable while backgrounded

        s.onForeground()
        assertEquals(banked, s.session?.elapsedMs) // the running window folds on the next tick/move
    }

    //endregion

    //region restore (A19 / rotation + process death)

    @Test
    fun `persist then adoptRestored restores the mid-play session`() {
        val repo = FakeCompletionRepository()
        val s = readyState(completions = repo)
        s.onDragEvent(DragEvent.Extend(GridCell(0, 0)))
        s.onDragEvent(DragEvent.Extend(GridCell(0, 1)))
        s.hint()
        val bytes = s.persist()!!

        val restored = ZipPlayState(
            daily = freshDaily(),
            completions = repo,
            scope = scope,
            today = { day },
        )
        restored.adoptRestored(bytes)

        // runningSince is intentionally NOT persisted (paused restore, F2 §2); every
        // scored/persisted field must come back identical.
        val a = s.session!!
        val b = restored.session!!
        assertEquals(a.path, b.path)
        assertEquals(a.hintsUsed, b.hintsUsed)
        assertEquals(a.elapsedMs, b.elapsedMs)
        assertEquals(a.timerStarted, b.timerStarted)
        assertEquals(a.completedAt, b.completedAt)
        assertEquals(a.ref, b.ref)
        assertEquals(a.puzzle, b.puzzle)
        assertTrue(restored.screen is ZipScreen.Ready)
        assertNull(requireReady(restored).degraded)

        // A restored live session still wins and writes its record once.
        playSolution(restored)
        assertEquals(1, recordsOf(repo).size)
    }

    @Test
    fun `a restored completed session never re-emits`() {
        val repo = FakeCompletionRepository()
        val s = readyState(completions = repo)
        playSolution(s)
        val bytes = s.persist()!!
        assertEquals(1, recordsOf(repo).size)

        val restored = ZipPlayState(
            daily = freshDaily(),
            completions = repo,
            scope = scope,
            today = { day },
        )
        restored.adoptRestored(bytes)

        assertTrue(restored.session?.isComplete == true)
        assertEquals(1, recordsOf(repo).size)
    }

    @Test
    fun `corrupt saved bytes fall back to a normal load`() {
        val daily = freshDaily()
        val s = state(daily)
        s.adoptRestored(byteArrayOf(1, 2, 3))
        assertTrue(s.screen is ZipScreen.Ready)
        assertEquals(1, daily.todayCalls(ZipDefinition.id))
    }

    //endregion

    //region renderer model (F2-1 call site)

    @Test
    fun `renderer model mirrors the committed session`() {
        val s = readyState()
        val idle = s.rendererModel()!!
        assertEquals(ZipRendererModel.Status.IDLE, idle.status)
        assertTrue(idle.cells.any { it.isStart })

        s.onDragEvent(DragEvent.Extend(GridCell(0, 0)))
        s.hint()
        val model = s.rendererModel()!!
        assertEquals(listOf(0, 1), model.path) // the hint auto-placed solution[1]
        assertEquals(PlayTestSupport.solution[1].idx, model.hintHighlight)
        assertEquals(ZipRendererModel.Status.PLAYING, model.status)
    }

    //endregion
}
