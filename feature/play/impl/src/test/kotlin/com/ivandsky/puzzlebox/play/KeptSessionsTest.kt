package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.data.repository.KeptSession
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeKeptSessionRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kept sessions (ADR-0010) over stand-in games: leave and background keep the live
 * play's session, a pick resumes a kept session of today through the restore path
 * without a fetch, the store owns the day filter, and a solve discards.
 */
class KeptSessionsTest {

    private val alpha = TestGames.alpha
    private val beta = TestGames.beta
    private val day = LocalDate.parse("2026-11-04")

    private val fakes = FakeGames(listOf(alpha, beta))
    private val daily = FakeDailyRepository().apply {
        setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha)))
        setToday(beta, DailyToday.Fresh(TestGames.puzzle(beta)))
    }
    private val kept = FakeKeptSessionRepository()
    private val scopes = mutableListOf<CoroutineScope>()

    /** A fresh store over the SAME kept sessions — a new one is a process restart. */
    private fun store() = PlaySessionStore(
        games = fakes.games,
        ui = fakes.registry,
        daily = daily,
        completions = FakeCompletionRepository(),
        kept = kept,
        today = { day },
        scopeFactory = { CoroutineScope(Job() + UnconfinedTestDispatcher()).also { scopes += it } },
        keepScope = CoroutineScope(Job() + UnconfinedTestDispatcher()),
    )

    private fun PlaySessionStore.pickNow(game: com.ivandsky.puzzlebox.model.GameId) = runBlocking { pick(game) {} }

    /** Picks [game], loads today's board, plays one move and lets [runningMs] pass. */
    private fun PlaySessionStore.played(game: com.ivandsky.puzzlebox.model.GameId, runningMs: Long = 30_000L) {
        pickNow(game)!!.load()
        fakes[game].latest.move()
        fakes[game].latest.advance(runningMs)
    }

    private fun KeptSession.session() = SessionBlob.decode(envelope)!!

    @Test
    fun `leave then pick resumes the same bytes with the elapsed folded in and never fetches`() {
        val store = store()
        store.played(alpha, runningMs = 42_000L)
        val fetches = daily.todayCalls(alpha)

        store.leave()

        val session = kept.current.getValue(alpha)
        assertEquals(day, session.day)
        assertEquals(42_000L, session.elapsedMs)
        assertEquals(alpha, session.session().game)
        assertEquals("fake|1|false|$day|null|42000", session.session().session.decodeToString())

        // The play is exposed only once adopted: the route never sees a needs-load play.
        val exposed = mutableListOf<Boolean>()
        val watcher = CoroutineScope(UnconfinedTestDispatcher())
        watcher.launch { store.active.collect { play -> play?.let { exposed += it.needsLoad } } }
        val resumed = store.pickNow(alpha)!!
        watcher.cancel()

        assertEquals(listOf(false), exposed)
        assertFalse(resumed.needsLoad)
        assertArrayEquals(session.session().session, fakes[alpha].latest.bytes())
        assertEquals(42_000L, fakes[alpha].latest.displayedElapsedMs())
        assertEquals(fetches, daily.todayCalls(alpha))
    }

    @Test
    fun `after a process restart a kept session resumes without calling today`() {
        store().apply { played(alpha, runningMs = 9_000L) }.leave()

        val reborn = store()
        val resumed = reborn.pickNow(alpha)!!

        assertFalse(resumed.needsLoad)
        assertEquals(1, fakes[alpha].latest.moves)
        assertEquals(9_000L, fakes[alpha].latest.displayedElapsedMs())
        assertEquals(1, daily.todayCalls(alpha)) // the first process's load only
    }

    @Test
    fun `a kept session of another day is discarded and the game fetches fresh`() {
        val stale = FakeController.envelope(alpha, moves = 5, day = day.minusDays(1), elapsedMs = 60_000L)
        runBlocking { kept.keep(KeptSession(alpha, day.minusDays(1), 60_000L, SessionBlob.encode(alpha, stale.session))) }
        val store = store()

        val play = store.pickNow(alpha)!!

        assertTrue(play.needsLoad)
        assertNull(kept.current[alpha])
        play.load()
        assertEquals(1, daily.todayCalls(alpha))
        assertEquals(0, fakes[alpha].latest.moves)
    }

    @Test
    fun `a solve discards the game's kept session`() {
        val store = store()
        store.played(alpha)
        store.background()
        assertTrue(kept.current.containsKey(alpha))

        fakes[alpha].latest.win()

        assertNull(kept.current[alpha])
        // The finished play keeps nothing on a later background or leave.
        store.background()
        store.leave()
        assertNull(kept.current[alpha])
    }

    @Test
    fun `two games are kept at once, one per game`() {
        val store = store()
        store.played(alpha, runningMs = 1_000L)
        store.leave()
        store.played(beta, runningMs = 2_000L)
        store.leave()

        assertEquals(setOf(alpha, beta), kept.current.keys)
        assertEquals(1_000L, kept.current.getValue(alpha).elapsedMs)
        assertEquals(2_000L, kept.current.getValue(beta).elapsedMs)
    }

    @Test
    fun `switching to another game keeps the released one`() {
        val store = store()
        store.played(alpha, runningMs = 3_000L)

        store.start(beta) {}

        assertEquals(3_000L, kept.current.getValue(alpha).elapsedMs)
        assertEquals(beta, store.active.value!!.gameId)
    }

    @Test
    fun `leave cancels the play's scope and only one play is ever live`() {
        val store = store()
        store.played(alpha)

        store.leave()

        assertNull(store.active.value)
        assertFalse(scopes.single().isActive)
        // The released controller's late change reaches no kept session.
        val before = kept.current.getValue(alpha)
        fakes[alpha].latest.move()
        store.background()
        assertEquals(before, kept.current.getValue(alpha))
    }

    @Test
    fun `background after the chrome's own fold keeps the same clock, never double-folded`() {
        val store = store()
        store.played(alpha, runningMs = 3_000L)
        val controller = fakes[alpha].latest

        controller.onBackground() // PlayChrome's ON_PAUSE fold
        store.background() // the route's ON_STOP
        store.background()

        assertEquals(1, controller.folds)
        assertEquals(3_000L, controller.displayedElapsedMs())
        assertEquals(3_000L, kept.current.getValue(alpha).elapsedMs)
        assertEquals("fake|1|false|$day|null|3000", kept.current.getValue(alpha).session().session.decodeToString())
        assertTrue(store.active.value != null)
    }

    @Test
    fun `a board never started keeps nothing`() {
        val store = store()
        store.pickNow(alpha)!!.load()

        store.leave()

        assertTrue(kept.current.isEmpty())
    }

    @Test
    fun `a replay of another day never overwrites today's kept session`() {
        val store = store()
        store.played(alpha, runningMs = 4_000L)
        store.leave()
        val today = kept.current.getValue(alpha)

        // History replay of an older day: played, then backed out of.
        store.restore(FakeController.envelope(alpha, moves = 2, day = day.minusDays(30), number = 3)) {}
        fakes[alpha].latest.move()
        fakes[alpha].latest.advance(1_000L)
        store.leave()

        assertEquals(today, kept.current.getValue(alpha))
    }

    @Test
    fun `a same-game history replay over today's live play keeps today's session first`() {
        val store = store()
        store.played(alpha, runningMs = 21_000L)

        // Home → History → a past day of the same game while today's play is still live.
        store.restore(FakeController.envelope(alpha, moves = 4, day = day.minusDays(30), number = 12)) {}

        assertEquals(day, kept.current.getValue(alpha).day)
        assertEquals(21_000L, kept.current.getValue(alpha).elapsedMs)
        assertEquals(4, fakes[alpha].latest.moves)
    }
}
