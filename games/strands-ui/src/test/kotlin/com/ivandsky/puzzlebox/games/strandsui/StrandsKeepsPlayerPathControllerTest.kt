package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.game.replay.MoveLog
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzle
import com.ivandsky.puzzlebox.games.strands.StrandsWord
import com.ivandsky.puzzlebox.games.strands.codec.StrandsCodec
import com.ivandsky.puzzlebox.games.strands.render.LinkKind
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** The path the player drew for a found word stays drawn: live, announced, after a reopen. */
class StrandsKeepsPlayerPathControllerTest {

    // 2 × 2 ABBA: the spangram reads A B B A along two orders over the same four cells.
    private val canonical = listOf(Coord(0, 0), Coord(0, 1), Coord(1, 0), Coord(1, 1))
    private val other = listOf(Coord(0, 0), Coord(1, 0), Coord(0, 1), Coord(1, 1))
    private val puzzle = StrandsPuzzle(
        rows = 2,
        cols = 2,
        letters = "ABBA",
        clue = "Pairs",
        spangram = StrandsWord("ABBA", canonical),
        themeWords = emptyList(),
        validWords = setOf("ABBA"),
        constructors = "Tess Tester",
    )

    private val saved = ArrayList<ByteArray>()

    private fun controller(log: MoveLog) = StrandsPlayController(
        env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), { saved += it }, {}, log),
        now = { Instant.EPOCH },
    )

    private fun StrandsPlayController.spangramLink(): List<Coord> =
        baseModel.links.single { it.kind == LinkKind.Spangram }.cells

    private fun played(path: List<Coord>): Pair<StrandsPlayController, MoveLog> {
        val log = MoveLog.fresh(StrandsUiFixtures.ref.gameId)
        val controller = controller(log)
        controller.adoptToday(StrandsCodec.envelope(puzzle), StrandsUiFixtures.ref)
        controller.onTrace(TraceStep.Submit(path))
        return controller to log
    }

    @Test
    fun `the board and the found event follow the submitted trace`() {
        val (controller, _) = played(other)

        assertEquals(other, controller.spangramLink())
        assertEquals(other, controller.takeFound()?.cells)
    }

    @Test
    fun `a reopened saved play keeps the submitted trace`() {
        val (_, log) = played(other)
        val reopened = controller(MoveLog.restored(StrandsUiFixtures.ref.gameId, log.snapshot()))

        assertNotNull(reopened.adoptRestored(saved.last()))

        assertEquals(other, reopened.spangramLink())
    }

    @Test
    fun `a reopened saved play of the canonical trace draws the canonical path`() {
        val (_, log) = played(canonical)
        val reopened = controller(MoveLog.restored(StrandsUiFixtures.ref.gameId, log.snapshot()))

        reopened.adoptRestored(saved.last())

        assertEquals(canonical, reopened.spangramLink())
    }
}
