package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The play session store over stand-in games: which play is live, how a saved or
 * replayed [SessionBlob] finds its game (C1: after process death the saveable outlives
 * the store), and which entry's saver receives the session bytes (F5/F6).
 */
class PlaySessionStoreTest {

    private val alpha = TestGames.alpha
    private val beta = TestGames.beta
    private val day = LocalDate.parse("2026-10-31")

    private val fakes = FakeGames(listOf(alpha, beta))
    private val daily = FakeDailyRepository().apply {
        setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha)))
        setToday(beta, DailyToday.Fresh(TestGames.puzzle(beta)))
    }
    private val scopes = mutableListOf<CoroutineScope>()

    private fun store(ui: GamePlayRegistry = fakes.registry) = PlaySessionStore(
        games = fakes.games,
        ui = ui,
        daily = daily,
        completions = FakeCompletionRepository(),
        today = { day },
        scopeFactory = { CoroutineScope(Job() + UnconfinedTestDispatcher()).also { scopes += it } },
    )

    private fun ByteArray.decoded() = SessionBlob.decode(this)!!

    @Test
    fun `playable lists every game with a play ui in registry order`() {
        assertEquals(listOf(alpha to "Alpha", beta to "Beta"), store().playable)
        // A plugin without a play registration is not playable.
        assertEquals(listOf(beta to "Beta"), store(GamePlayRegistry(listOf(fakes[beta]))).playable)
    }

    @Test
    fun `start never fetches and an unplayable id returns null untouched`() {
        val store = store()
        val play = store.start(alpha) {}!!
        assertEquals(0, daily.todayCalls(alpha))
        assertTrue(play.needsLoad)

        assertNull(store.start(GameId("not-a-game")) {})
        assertNull(PlaySessionStore(fakes.games, GamePlayRegistry(emptyList()), daily, FakeCompletionRepository()).start(alpha) {})
        assertSame(play, store.active.value)
    }

    @Test
    fun `a dead store is restarted for the saved game and adopts the session`() {
        // Process death: a brand-new store (Koin single reborn), nothing live.
        val store = store()
        assertNull(store.active.value)

        var persisted: ByteArray? = null
        val restored = store.restore(FakeController.envelope(alpha, moves = 1, day = day)) { persisted = it }

        assertSame(restored, store.active.value)
        assertEquals(alpha, restored!!.gameId)
        assertEquals(1, fakes[alpha].latest.moves)
        assertEquals(0, daily.todayCalls(alpha)) // adopted, never fetched
        // The consumed saveable is handed back so the NEXT process death keeps it.
        assertEquals(alpha, persisted!!.decoded().game)
    }

    @Test
    fun `a live same-game play is adopted in place without a restart`() {
        val store = store()
        val live = store.start(alpha) {}
        // Rotation: the store survived; the saveable re-adopts into the SAME play.
        val restored = store.restore(FakeController.envelope(alpha, moves = 3, day = day)) {}
        assertSame(live, restored)
        assertEquals(3, fakes[alpha].latest.moves)
    }

    @Test
    fun `a rotation restore of the live session keeps the live controller`() {
        val store = store()
        val sink = mutableListOf<ByteArray>()
        val play = store.start(alpha) { sink += it }!!
        play.load()
        val live = fakes[alpha].latest
        live.move()
        val created = fakes[alpha].controllers.size

        // Rotation: the store survived and the reborn saveable hands back exactly the
        // session the live controller published — its transient state must survive.
        val restored = store.restore(sink.last().decoded()) { sink += it }

        assertSame(play, restored)
        assertSame(live, play.controller)
        assertEquals(created, fakes[alpha].controllers.size)
        assertEquals(PlayBoardStatus.Ready(banner = null), play.board.value.status)
        // Still the live generation: its changes keep reaching the new sink.
        val writes = sink.size
        live.move()
        assertEquals(writes + 1, sink.size)
        assertEquals(2, sink.last().decoded().session.decodeToString().split('|')[1].toInt())
    }

    @Test
    fun `a stale other-game play is released before the saved game starts`() {
        val store = store()
        val stale = store.start(beta) {}
        store.restore(FakeController.envelope(alpha, moves = 1, day = day)) {}

        val active = store.active.value!!
        assertNotSame(stale, active)
        assertEquals(alpha, active.gameId)
        assertEquals(1, fakes[alpha].latest.moves)
        assertFalse("the released play's scope is cancelled", scopes.first().isActive)
    }

    @Test
    fun `an unknown or unreadable envelope leaves the store untouched`() {
        val store = store()
        val live = store.start(alpha) {}

        assertNull(store.restore(SessionBlob.Decoded(game = GameId("not-a-game"), session = ByteArray(0))) {})
        assertNull(store.restore(null) {})
        assertSame(live, store.active.value)
    }

    @Test
    fun `a corrupt saved session falls back to a fresh fetch`() {
        val store = store()
        val play = store.restore(SessionBlob.Decoded(alpha, "garbage".encodeToByteArray())) {}!!
        assertEquals(1, daily.todayCalls(alpha))
        assertTrue(play.board.value.status is com.ivandsky.puzzlebox.gameui.PlayBoardStatus.Ready)
    }

    @Test
    fun `the session-changed callback emits game-named envelope bytes`() {
        val store = store()
        var persisted: ByteArray? = null
        store.start(beta) { persisted = it }!!.load()

        // The route's saveable carries the envelope, not bare codec bytes — this is
        // what lets a process-dead store recover WHICH game to restart (C1).
        val decoded = persisted!!.decoded()
        assertEquals(beta, decoded.game)
        assertEquals(fakes[beta].latest.bytes().decodeToString(), decoded.session.decodeToString())
    }

    @Test
    fun `a re-entered entry rebinds the sink and is backfilled with the live session`() {
        val store = store()
        val first = mutableListOf<ByteArray>()
        store.start(alpha) { first += it }!!.load()
        fakes[alpha].latest.move()
        val handedToFirst = first.size

        // Rotation: a NEW saveable starts the same game; it gets the live blob at once…
        val second = mutableListOf<ByteArray>()
        store.start(alpha) { second += it }
        assertEquals(1, second.size)
        assertEquals(fakes[alpha].latest.bytes().decodeToString(), second.single().decoded().session.decodeToString())

        // …and every later change goes to it, never to the dead first composition.
        fakes[alpha].latest.move()
        assertEquals(handedToFirst, first.size)
        assertEquals(2, second.size)
    }

    @Test
    fun `F5 switching games rebinds the sink and the released play can no longer publish`() {
        val store = store()
        val alphaSink = mutableListOf<ByteArray>()
        store.start(alpha) { alphaSink += it }!!.load()
        val alphaController = fakes[alpha].latest
        val alphaWrites = alphaSink.size

        val betaSink = mutableListOf<ByteArray>()
        store.start(beta) { betaSink += it }!!.load()
        assertTrue(betaSink.isNotEmpty())
        assertTrue(betaSink.all { it.decoded().game == beta })

        // A late change from the released alpha play reaches nobody.
        alphaController.move()
        assertEquals(alphaWrites, alphaSink.size)
        assertTrue(betaSink.all { it.decoded().game == beta })
    }

    @Test
    fun `F6 end releases the play, cancels its scope and drops the sink`() {
        val store = store()
        val sink = mutableListOf<ByteArray>()
        store.start(alpha) { sink += it }!!.load()
        val controller = fakes[alpha].latest
        val writes = sink.size

        store.end()

        assertNull(store.active.value)
        assertFalse(scopes.single().isActive)
        controller.move()
        assertEquals(writes, sink.size)

        // The next entry picks again: nothing is resumed or backfilled.
        val next = mutableListOf<ByteArray>()
        val fresh = store.start(alpha) { next += it }
        assertNotNull(fresh)
        assertTrue(next.isEmpty())
        assertTrue(fresh!!.needsLoad)
    }

    @Test
    fun `a superseded controller's late win writes nothing`() {
        val completions = FakeCompletionRepository()
        val store = PlaySessionStore(fakes.games, fakes.registry, daily, completions, today = { day }, scopeFactory = {
            CoroutineScope(Job() + UnconfinedTestDispatcher())
        })
        daily.setHistory(alpha, listOf(DailyEntry(alpha, day, 7, "alpha/v1", 0L)))
        val play = store.start(alpha) {}!!
        play.load()
        val superseded = fakes[alpha].latest
        play.load() // retry → a new controller

        superseded.win()
        assertTrue(completions.observeRecordsNow().isEmpty())
    }

    private fun FakeCompletionRepository.observeRecordsNow() =
        runBlocking { observeRecords().first() }
}
