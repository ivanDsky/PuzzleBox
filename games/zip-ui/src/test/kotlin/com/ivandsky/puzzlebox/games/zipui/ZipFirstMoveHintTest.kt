package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.replay.MoveLog
import com.ivandsky.puzzlebox.game.replay.ReplayCodec
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove.Extend
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove.Hint
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Hint button through the real controller: what
 * lands on the board, what is counted and what the replay log holds. The fixture's branchy board
 * has one way on from cells 0 to 3, then a real choice between 4 and 8.
 */
class ZipFirstMoveHintTest {

    private val log = MoveLog.fresh(ZipDefinition.id)
    private var clock: Instant = Instant.parse("2026-11-16T10:00:00Z")

    private fun play(envelope: Puzzle): ZipPlayController {
        val controller = ZipPlayController(
            env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {}, log),
            now = { clock },
        )
        assertEquals(Adopt.Adopted, controller.adoptToday(envelope, ZipPlayFixture.ref(LocalDate.of(2026, 11, 16))))
        return controller
    }

    private fun branchy() = play(ZipPlayFixture.envelope(ZipPlayFixture.branchyPuzzle()))

    private fun ZipPlayController.drag(vararg cells: Int) {
        for (idx in cells) {
            clock = clock.plusMillis(100)
            onDragEvent(DragEvent.Extend(GridCell(idx / 5, idx % 5)))
        }
        clock = clock.plusMillis(100)
    }

    /** The log's moves, each with its play-time stamp. */
    private fun recorded(): List<Pair<Long, ZipReplayMove?>> =
        checkNotNull(log.snapshot()?.let(ReplayCodec::decode)?.getOrNull()) { "the play has no readable log" }
            .moves.map { it.atMs to ZipReplayMove.of(it.move, 25) }

    private fun cells(vararg idx: Int) = idx.map(::Cell)

    @Test
    fun `on an empty board the hint walks the known cells and lands on the first real choice`() {
        val c = branchy()

        c.hint()

        assertEquals(cells(0, 1, 2, 3, 4), c.session!!.path)
        assertEquals(4, c.hintCell)
        assertEquals(HintArrow(headIdx = 4, tailIdx = 3), c.hintArrow)
        assertEquals(HintEvent(atMs = clock.toEpochMilli(), fromIdx = null, placedIdx = 4), c.hintHistory.single())
    }

    @Test
    fun `the walked cells are plain moves before the hint, and the hint is still one count and one cell`() {
        val c = branchy()

        c.hint()

        assertEquals(1, c.session!!.hintsUsed)
        assertEquals(
            listOf(Extend(Cell(0)), Extend(Cell(1)), Extend(Cell(2)), Extend(Cell(3)), Hint, Extend(Cell(4))),
            recorded().map { it.second },
        )
        assertEquals("one press, one stamp", 1, recorded().map { it.first }.distinct().size)
    }

    @Test
    fun `mid trace the hint walks on from the head`() {
        val c = branchy()
        c.drag(0, 1)

        c.hint()

        assertEquals(cells(0, 1, 2, 3, 4), c.session!!.path)
        assertEquals(listOf(Extend(Cell(2)), Extend(Cell(3)), Hint, Extend(Cell(4))), recorded().drop(2).map { it.second })
        assertEquals(1, c.session!!.hintsUsed)
    }

    @Test
    fun `a board with one way all along still never gets its start cell as the hint`() {
        val c = play(ZipPlayFixture.envelope())

        c.hint()

        assertEquals(cells(0, 1), c.session!!.path)
        assertEquals(1, c.hintCell)
        assertEquals(listOf(Extend(Cell(0)), Hint, Extend(Cell(1))), recorded().map { it.second })
        assertEquals(1, c.session!!.hintsUsed)
    }
}
