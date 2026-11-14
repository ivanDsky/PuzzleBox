package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.Restored
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.games.zip.engine.ZipValidation
import com.ivandsky.puzzlebox.games.zip.engine.ZipVariants
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel
import com.ivandsky.puzzlebox.gameui.playCaption
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ticket 13 controller contract: adopt/trust gate, move validation, hints, win emission, restore. */
class ZipPlayControllerTest {

    private val day: LocalDate = LocalDate.of(2026, 10, 31)
    private val ref: ZipPuzzleRef = ZipPlayFixture.ref(day)
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private val saved = mutableListOf<ByteArray>()
    private val won = mutableListOf<CompletionRecord>()

    private fun controller(
        now: () -> Instant = Instant::now,
        saved: MutableList<ByteArray> = this.saved,
        won: MutableList<CompletionRecord> = this.won,
    ) = ZipPlayController(PlayEnvironment(scope, { saved += it }, { won += it }), now)

    /** Adopts the fixture under today's identity and returns the live controller. */
    private fun readyController(
        envelope: Puzzle = ZipPlayFixture.envelope(),
        now: () -> Instant = Instant::now,
    ): ZipPlayController = controller(now).also {
        check(it.adoptToday(envelope, ref) == Adopt.Adopted) { "fixture did not adopt" }
    }

    private fun playSolution(c: ZipPlayController) {
        ZipPlayFixture.solution.forEach { cell ->
            c.onDragEvent(DragEvent.Extend(GridCell(cell.idx / 5, cell.idx % 5)))
        }
    }

    private fun extend(c: ZipPlayController, vararg idx: Int) =
        idx.forEach { c.onDragEvent(DragEvent.Extend(GridCell(it / 5, it % 5))) }

    //region adopt / trust gate (A16)

    @Test
    fun `fresh adopt takes today's identity on an idle board`() {
        val c = controller()
        assertNull(c.session)
        assertEquals(Adopt.Adopted, c.adoptToday(ZipPlayFixture.envelope(), ref))

        assertEquals(ZipPuzzleRef(PuzzleKey.Daily(day, 592)), c.session?.ref)
        assertEquals(emptyList<Cell>(), c.session?.path)
        assertEquals(ZipRendererModel.Status.IDLE, c.rendererModel()?.status)
        assertFalse(c.isComplete)
    }

    @Test
    fun `untrustable payload is never playable (A16)`() {
        val garbage = Puzzle(
            gameId = ZipDefinition.id,
            variant = ZipVariants.byId(VariantId("zip/n5"))!!.id,
            formatVersion = 1,
            payload = byteArrayOf(99),
        )
        val c = controller()
        val adopt = c.adoptToday(garbage, ref)
        assertTrue(adopt is Adopt.Rejected)
        assertTrue((adopt as Adopt.Rejected).message.isNotEmpty())
        assertNull(c.session)
    }

    //endregion

    //region moves (F2 §7 authoritative validation)

    @Test
    fun `valid drag extends the trace`() {
        val c = readyController()
        c.onDragEvent(DragEvent.Extend(GridCell(0, 0)))
        c.onDragEvent(DragEvent.Extend(GridCell(0, 1)))

        assertEquals(listOf(Cell(0), Cell(1)), c.session?.path)
        assertEquals(true, c.session?.timerStarted)
        assertNull(c.hintCell)
    }

    @Test
    fun `an invalid extend is one mistake and one rejection, path untouched`() {
        val c = readyController()
        c.onDragEvent(DragEvent.Extend(GridCell(2, 2))) // not waypoint 1 → WrongStart

        assertEquals(1, c.session?.mistakeCount)
        assertEquals(emptyList<Cell>(), c.session?.path)
        assertTrue(c.feedback.rejectedMove?.reason is ZipValidation.Reason.WrongStart)
        assertEquals("Start at 1", playCaption(c, c))
    }

    @Test
    fun `an out-of-order drag yields the rejection caption until the next accepted move`() {
        // Wall-free board: 0→4 then down the right edge reaches waypoint 3 (24) before 2 (12).
        val open = ZipPlayFixture.puzzle().let { it.copy(board = it.board.copy(walls = emptySet())) }
        val c = readyController(envelope = ZipPlayFixture.envelope(open))
        extend(c, 0, 1, 2, 3, 4, 9, 14, 19)
        extend(c, 24)

        assertEquals(8, c.session?.path?.size)
        assertTrue(c.feedback.rejectedMove?.reason is ZipValidation.Reason.WrongWaypointOrder)
        assertEquals("Go to 2 first", c.rejection)
        assertEquals("Go to 2 first", playCaption(c, c))

        extend(c, 18) // adjacent to 19, not a waypoint: accepted
        assertNull(playCaption(c, c))
    }

    @Test
    fun `a board-refused waypoint skip is explained but never counted, a far wander stays silent`() {
        val open = ZipPlayFixture.puzzle().let { it.copy(board = it.board.copy(walls = emptySet())) }
        val c = readyController(envelope = ZipPlayFixture.envelope(open))
        extend(c, 0, 1, 2, 3, 4, 9, 14, 19)
        val mistakes = c.session?.mistakeCount

        c.onDragEvent(DragEvent.Refused(GridCell(4, 0))) // untraced, far from the tail → NotAdjacent
        assertNull(playCaption(c, c))

        c.onDragEvent(DragEvent.Refused(GridCell(4, 4))) // waypoint 3 before 2
        assertEquals("Go to 2 first", playCaption(c, c))
        assertEquals(mistakes, c.session?.mistakeCount)
        assertEquals(8, c.session?.path?.size)
    }

    @Test
    fun `a plain hint has no caption, a trimming hint says what it fixed`() {
        val plain = readyController()
        plain.hint()
        assertNull(playCaption(plain, plain))

        val c = readyController(envelope = ZipPlayFixture.envelope(ZipPlayFixture.branchyPuzzle()))
        ZipPlayFixture.deadTrace().forEach { c.onDragEvent(DragEvent.Extend(GridCell(it.idx / 5, it.idx % 5))) }
        c.hint()

        assertEquals("Fixed your path up to 1", c.hintCaption)
        assertEquals("Fixed your path up to 1", playCaption(c, c))

        extend(c, 9) // the next solution cell after the auto-placed 4: accepted
        assertNull(playCaption(c, c))
    }

    @Test
    fun `retract event trims and counts a backtrack`() {
        val c = readyController()
        extend(c, 0, 1, 2)
        c.onDragEvent(DragEvent.Retract(index = 0))

        assertEquals(listOf(Cell(0)), c.session?.path)
        assertEquals(1, c.session?.backtrackCount)
    }

    @Test
    fun `clear event empties the trace to the start state and counts one backtrack`() {
        val c = readyController()
        extend(c, 0, 1, 2)
        c.onDragEvent(DragEvent.Clear) // tap on the first traced cell (R1)

        assertEquals(emptyList<Cell>(), c.session?.path)
        assertEquals(1, c.session?.backtrackCount)
        assertEquals(ZipRendererModel.Status.IDLE, c.rendererModel()?.status)

        // The emptied trace redraws from the start like a fresh session.
        c.onDragEvent(DragEvent.Extend(GridCell(0, 0)))
        assertEquals(listOf(Cell(0)), c.session?.path)
    }

    @Test
    fun `the clear seam empties the trace as one undo unit`() {
        val c = readyController()
        extend(c, 0, 1, 2)

        c.edits.clear()
        assertEquals(emptyList<Cell>(), c.session?.path)

        c.undo()
        assertEquals(listOf(Cell(0), Cell(1), Cell(2)), c.session?.path)
    }

    @Test
    fun `clear on an empty trace changes nothing`() {
        val c = readyController()
        val before = saved.size
        val session = c.session

        c.edits.clear()

        assertEquals(session, c.session)
        assertEquals(before, saved.size)
        assertFalse(c.canUndo)
    }

    //endregion

    //region undo / redo (PlayUndo over the session's command history)

    @Test
    fun `undo and redo step the trace, clear the hint arrow and persist each step`() {
        val c = readyController()
        extend(c, 0, 1, 2)
        assertTrue(c.canUndo)
        assertFalse(c.canRedo)

        c.hint() // places cell 3 with an arrow
        assertTrue(c.hintArrow != null)
        val beforeUndo = saved.size
        c.undo()
        assertNull(c.hintArrow)
        assertEquals(ZipPlayFixture.solution.take(3), c.session?.path)
        assertTrue(c.canRedo)
        assertArrayEquals(c.persist(), saved.last())

        c.redo()
        assertEquals(ZipPlayFixture.solution.take(4), c.session?.path)
        assertFalse(c.canRedo)
        assertEquals(beforeUndo + 2, saved.size)

        // nothing to redo: no state change, nothing persisted
        c.redo()
        assertEquals(beforeUndo + 2, saved.size)
    }

    @Test
    fun `undo leaves hints, mistakes, backtracks and the timer as played`() {
        val tick = AtomicLong(1_000)
        val c = readyController(now = { Instant.ofEpochMilli(tick.getAndAdd(100)) })
        extend(c, 0, 1, 2)
        c.onDragEvent(DragEvent.Extend(GridCell(4, 4))) // rejected: a mistake
        c.onDragEvent(DragEvent.Retract(index = 0)) // a backtrack
        c.hint()
        val before = c.session!!

        c.undo()
        c.undo()

        val after = c.session!!
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
        val c = readyController()
        extend(c, 0, 1, 2)
        c.undo()
        assertTrue(c.canRedo)

        c.onDragEvent(DragEvent.Extend(GridCell(4, 4))) // rejected: not a move
        assertTrue(c.canRedo)

        extend(c, 2) // an accepted move: the old future is gone
        assertEquals(ZipPlayFixture.solution.take(3), c.session?.path)
        assertFalse(c.canRedo)
    }

    @Test
    fun `a finished play is frozen against undo and redo`() {
        val c = readyController()
        playSolution(c)
        assertTrue(c.session!!.isComplete)
        assertFalse(c.canUndo)
        assertFalse(c.canRedo)
        val solved = c.session
        c.undo()
        c.redo()
        assertEquals(solved, c.session)
    }

    @Test
    fun `history survives persist and restore`() {
        val c = readyController()
        extend(c, 0, 1, 2)
        c.undo()

        val restored = controller()
        restored.adoptRestored(c.persist()!!)

        assertTrue(restored.canUndo)
        assertTrue(restored.canRedo)
        restored.redo()
        assertEquals(ZipPlayFixture.solution.take(3), restored.session?.path)
        restored.undo()
        restored.undo()
        assertEquals(ZipPlayFixture.solution.take(1), restored.session?.path)
    }

    //endregion

    //region hints (A3 / F2 §10)

    @Test
    fun `hint on a fresh board reveals one step and counts one hint`() {
        val c = readyController()
        c.hint()

        assertEquals(1, c.session?.hintsUsed)
        // Empty trace: the smallest help is the start cell itself — auto-placed (ticket
        // 28), no arrow because a lone cell has no segment direction.
        assertEquals(listOf(ZipPlayFixture.solution[0]), c.session?.path)
        assertEquals(ZipPlayFixture.solution[0].idx, c.hintCell)
        assertNull(c.hintArrow)
    }

    @Test
    fun `hint on a dead trace trims free, autoplaces the step, counts one hint`() {
        val c = readyController(envelope = ZipPlayFixture.envelope(ZipPlayFixture.branchyPuzzle()))
        ZipPlayFixture.deadTrace().forEach { c.onDragEvent(DragEvent.Extend(GridCell(it.idx / 5, it.idx % 5))) }
        assertEquals(5, c.session?.path?.size)

        c.hint()

        assertEquals(1, c.session?.hintsUsed)
        // Trimmed to the longest solvable prefix, then the ONE counted hint AUTO-PLACES
        // the next solution segment (ticket 28) with its arrow.
        assertEquals(ZipPlayFixture.solution.take(5), c.session?.path)
        assertEquals(ZipPlayFixture.solution[4].idx, c.hintCell)
        assertEquals(
            HintArrow(
                headIdx = ZipPlayFixture.solution[4].idx,
                tailIdx = ZipPlayFixture.solution[3].idx,
            ),
            c.hintArrow,
        )
    }

    @Test
    fun `an accepted move clears the hint highlight and the arrow`() {
        val c = readyController()
        c.hint() // auto-places the start
        c.onDragEvent(DragEvent.Extend(GridCell(0, 1))) // solution[1], accepted

        assertNull(c.hintCell)
        assertNull(c.hintArrow)
    }

    @Test
    fun `hint arrow dies on any next input`() {
        val c = readyController()
        c.onDragEvent(DragEvent.Extend(GridCell(0, 0)))
        c.hint()
        assertEquals(HintArrow(headIdx = 1, tailIdx = 0), c.hintArrow)

        // Even a rejected input (a tap that cannot extend) kills the arrow: next input.
        c.onDragEvent(DragEvent.Extend(GridCell(4, 4)))
        assertNull(c.hintArrow)
    }

    @Test
    fun `hint telemetry records each press's origin and placement`() {
        var clockMs = 0L
        val c = readyController(now = { Instant.ofEpochMilli(clockMs) })
        c.hint() // places the start (empty trace, no arrow)
        assertEquals(1, c.session?.hintsUsed)
        assertEquals(
            listOf(HintEvent(atMs = 0L, fromIdx = null, placedIdx = ZipPlayFixture.solution[0].idx)),
            c.hintHistory,
        )

        clockMs += 5_000L
        c.hint() // places the next solution segment
        assertEquals(2, c.session?.hintsUsed)
        assertEquals(
            listOf(
                HintEvent(atMs = 5_000L, fromIdx = 0, placedIdx = ZipPlayFixture.solution[1].idx),
            ),
            c.hintHistory.drop(1),
        )
    }

    @Test
    fun `play-again resets the hint arrow`() {
        val c = readyController()
        c.onDragEvent(DragEvent.Extend(GridCell(0, 0)))
        c.hint()
        assertEquals(HintArrow(headIdx = 1, tailIdx = 0), c.hintArrow)

        c.playAgain()
        assertNull(c.hintArrow)
        assertNull(c.hintCell)
    }

    //endregion

    //region win flow (A4 / F2 §7)

    @Test
    fun `completing the board wins once and emits exactly one record`() {
        val tick = AtomicLong(0)
        val c = readyController(now = { Instant.ofEpochMilli(tick.getAndAdd(100)) })

        playSolution(c)

        assertTrue(c.isComplete)
        val elapsed = c.session?.elapsedMs ?: 0L
        assertTrue(elapsed > 0)
        assertEquals(1, won.size)
        assertEquals(ref, won.single().puzzleRef)
        assertEquals(elapsed, won.single().time)
        assertEquals(0, won.single().hints)
        // The completed session accepts no further moves (one play-through, one completion).
        c.onDragEvent(DragEvent.Extend(GridCell(0, 0)))
        assertEquals(1, won.size)
    }

    @Test
    fun `play-again after a win emits again`() {
        val c = readyController()
        playSolution(c)
        c.playAgain()
        assertNull(c.session?.completedAt)
        assertFalse(c.isComplete)
        assertEquals(ref, c.session?.ref)

        playSolution(c)

        assertEquals(2, won.size)
        assertEquals(ref, won.last().puzzleRef)
    }

    //endregion

    //region timer (F2 §2)

    /** A controller on a hand-wound clock; [ms] is the wall time every `now()` reads. */
    private class Wall(var ms: Long = 1_000) {
        val now: () -> Instant = { Instant.ofEpochMilli(ms) }
    }

    /** What the timer bar reads after the loop's next tick. */
    private fun ZipPlayController.shown(): Long {
        tick()
        return displayedElapsedMs()
    }

    @Test
    fun `a fresh puzzle's timer runs with no move made`() {
        val wall = Wall()
        val c = readyController(now = wall.now)

        wall.ms += 4_000

        assertTrue(c.session!!.path.isEmpty())
        assertEquals(4_000L, c.shown())
    }

    @Test
    fun `a restored in-progress session continues from its saved time and advances`() {
        val wall = Wall()
        val first = readyController(now = wall.now)
        extend(first, 0, 1)
        wall.ms += 3_000
        first.onBackground()
        val bytes = first.persist()!!

        wall.ms += 60_000 // process-death gap: never counted
        val restored = controller(wall.now)
        restored.adoptRestored(bytes)
        assertEquals(3_000L, restored.shown())

        wall.ms += 2_000
        assertEquals(5_000L, restored.shown())
    }

    @Test
    fun `a restored untouched session's timer runs too`() {
        val wall = Wall()
        val first = readyController(now = wall.now)
        first.onBackground()
        val bytes = first.persist()!!

        val restored = controller(wall.now)
        restored.adoptRestored(bytes)
        wall.ms += 1_500

        assertEquals(1_500L, restored.shown())
    }

    @Test
    fun `background banks the window and foreground resumes it without a move`() {
        val wall = Wall()
        val c = readyController(now = wall.now)
        extend(c, 0)
        wall.ms += 2_000

        c.onBackground()
        assertNull(c.clockWindow)
        assertEquals(2_000L, c.session?.elapsedMs)
        wall.ms += 30_000 // away: never counted
        assertEquals(2_000L, c.shown())

        c.onForeground()
        assertNotNull(c.clockWindow)
        wall.ms += 1_000
        assertEquals(3_000L, c.shown())
    }

    @Test
    fun `foreground on an untouched board reopens the window`() {
        val wall = Wall()
        val c = readyController(now = wall.now)
        wall.ms += 1_000
        c.onBackground()
        assertEquals(1_000L, c.session?.elapsedMs)

        c.onForeground()
        wall.ms += 500

        assertEquals(1_500L, c.shown())
    }

    @Test
    fun `completing the board stops the clock`() {
        val wall = Wall()
        val c = readyController(now = wall.now)
        wall.ms += 2_000
        playSolution(c)
        val frozen = c.session!!.elapsedMs

        assertTrue(c.isComplete)
        assertNull(c.clockWindow)
        assertEquals(2_000L, frozen)
        wall.ms += 9_000
        c.onForeground() // a completed play never reopens
        assertNull(c.clockWindow)
        assertEquals(frozen, c.shown())
    }

    @Test
    fun `play-again restarts from zero and advances at once`() {
        val wall = Wall()
        val c = readyController(now = wall.now)
        wall.ms += 2_000
        playSolution(c)

        c.playAgain()
        assertEquals(0L, c.shown())
        wall.ms += 700

        assertEquals(700L, c.shown())
    }

    //endregion

    //region restore (A19 / rotation + process death)

    @Test
    fun `persist then adoptRestored restores the mid-play session`() {
        val c = readyController()
        c.onDragEvent(DragEvent.Extend(GridCell(0, 0)))
        c.onDragEvent(DragEvent.Extend(GridCell(0, 1)))
        c.hint()
        val bytes = c.persist()!!

        val restoredWon = mutableListOf<CompletionRecord>()
        val restored = controller(won = restoredWon)
        assertEquals(Restored(ref), restored.adoptRestored(bytes))

        // runningSince is intentionally NOT persisted (paused restore, F2 §2); every
        // scored/persisted field must come back identical.
        val a = c.session!!
        val b = restored.session!!
        assertEquals(a.path, b.path)
        assertEquals(a.hintsUsed, b.hintsUsed)
        assertEquals(a.elapsedMs, b.elapsedMs)
        assertEquals(a.timerStarted, b.timerStarted)
        assertEquals(a.completedAt, b.completedAt)
        assertEquals(a.ref, b.ref)
        assertEquals(a.puzzle, b.puzzle)

        // A restored live session still wins and emits its record once.
        playSolution(restored)
        assertEquals(1, restoredWon.size)
        assertEquals(ref, restoredWon.single().puzzleRef)
        assertEquals(1, restoredWon.single().hints)
    }

    @Test
    fun `a restored completed session never re-emits`() {
        val c = readyController()
        playSolution(c)
        val bytes = c.persist()!!
        assertEquals(1, won.size)

        val restoredWon = mutableListOf<CompletionRecord>()
        val restored = controller(won = restoredWon)
        restored.adoptRestored(bytes)

        assertTrue(restored.isComplete)
        assertTrue(restoredWon.isEmpty())
    }

    @Test
    fun `corrupt saved bytes are unreadable and adopt nothing`() {
        val c = controller()
        assertNull(c.adoptRestored(byteArrayOf(1, 2, 3)))
        assertNull(c.session)
    }

    //endregion

    //region renderer model (F2-1 call site)

    @Test
    fun `renderer model mirrors the committed session`() {
        val c = readyController()
        val idle = c.rendererModel()!!
        assertEquals(ZipRendererModel.Status.IDLE, idle.status)
        assertTrue(idle.cells.any { it.isStart })

        c.onDragEvent(DragEvent.Extend(GridCell(0, 0)))
        c.hint()
        val model = c.rendererModel()!!
        assertEquals(listOf(0, 1), model.path) // the hint auto-placed solution[1]
        assertEquals(ZipPlayFixture.solution[1].idx, model.hintHighlight)
        assertEquals(ZipRendererModel.Status.PLAYING, model.status)
    }

    //endregion
}
