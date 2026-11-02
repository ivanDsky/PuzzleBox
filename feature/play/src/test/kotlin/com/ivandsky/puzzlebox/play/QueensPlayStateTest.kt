package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.repository.QueensDailyEntry
import com.ivandsky.puzzlebox.data.repository.QueensToday
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.queens.codec.QueensSessionCodec
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** queens-10 wiring contract: DI-shaped fetch → real session → record write on win,
 *  restore, and no-re-emit — mirroring ZipPlayStateTest's scope conventions. */
class QueensPlayStateTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun state(
        daily: FakeQueensDailyRepository,
        completions: FakeCompletionRepository = FakeCompletionRepository(),
    ) = QueensPlayState(
        daily = daily,
        completions = completions,
        scope = scope,
        now = { Instant.ofEpochMilli(1_000L) },
    ) to completions

    /** Loads the fixture synchronously (Unconfined scope) and returns the ready state. */
    private fun readyBoard(
        daily: FakeQueensDailyRepository = FakeQueensDailyRepository(
            todayResult = QueensToday.Fresh(QueensPlayFixtures.envelope()),
            history = listOf(QueensDailyEntry(FakeQueensDailyRepository.today(), 42, "queens/n8", 0L)),
        ),
        completions: FakeCompletionRepository = FakeCompletionRepository(),
    ): Pair<QueensPlayState, FakeCompletionRepository> {
        val (state, fake) = state(daily, completions)
        state.load()
        return state to fake
    }

    @Test
    fun `load fetches today and binds a real session with the day's ref`() {
        val (state, _) = readyBoard()

        val ready = state.screen as? QueensScreen.Ready
        assertNotNull(ready)
        assertEquals(QueensPlayFixtures.puzzle(), ready!!.puzzle)
        val board = state.board
        assertNotNull(board)
        assertEquals(QueensPuzzleRef(FakeQueensDailyRepository.today(), 42), board!!.session.ref)
        assertFalse(board.session.isComplete)
    }

    @Test
    fun `completing the board records one history entry exactly as the zip path does`() {
        val (state, completions) = readyBoard()
        val board = state.board!!

        // The §5 tap cycle: markless → MANUAL X → queen — two taps per solution cell.
        for (cell in QueensPlayFixtures.solution) {
            board.onTap(cell.idx)
            board.onTap(cell.idx)
        }

        assertTrue(board.session.isComplete)
        assertTrue(board.winEmitted)
        val records = runBlocking { completions.observeRecords().first() }
        assertEquals(1, records.size)
        val record = records.single()
        assertEquals(QueensPuzzleRef(FakeQueensDailyRepository.today(), 42), record.puzzleRef)
        assertEquals("queens/n8", variantOf(record))
    }

    @Test
    fun `unavailable fetch is a first-class error state`() {
        val (state, _) = state(FakeQueensDailyRepository.unavailable())
        state.load()
        assertTrue(state.screen is QueensScreen.Unavailable)
        assertNull(state.board)
    }

    @Test
    fun `a corrupt restored blob falls back to a fresh load`() {
        val (state, _) = readyBoard()
        state.adoptRestored(byteArrayOf(9, 9, 9))
        // The fixture fetch succeeds, so the fallback re-fetches and binds a new board.
        assertTrue(state.screen is QueensScreen.Ready)
        assertNotNull(state.board)
    }

    @Test
    fun `a restored completed session shows the win without re-emitting`() {
        // Play to completion, capture the session bytes.
        val (state, completions) = readyBoard()
        val board = state.board!!
        for (cell in QueensPlayFixtures.solution) {
            board.onTap(cell.idx)
            board.onTap(cell.idx)
        }
        val bytes = QueensSessionCodec.serialize(board.session)

        // A fresh entry adopts the same completed session — no second write (A4).
        val (restored, freshCompletions) = state(
            FakeQueensDailyRepository(QueensToday.Fresh(QueensPlayFixtures.envelope())),
        )
        restored.adoptRestored(bytes)
        assertTrue(restored.board!!.session.isComplete)
        assertFalse(restored.board!!.winEmitted)
        assertEquals(0, runBlocking { freshCompletions.observeRecords().first().size })
    }

    private fun variantOf(record: com.ivandsky.puzzlebox.game.CompletionRecord): String {
        // The stats blob is a flat JSON object built in insertion order: v then variant.
        val blob = record.stats.blob
        return blob.substringAfter("\"variant\":").substringBefore("}").trim('"')
    }
}
