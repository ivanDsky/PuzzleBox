package com.ivandsky.puzzlebox.app.e2e

import androidx.room.Room
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.di.dataModule
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.PastDayOutcome
import com.ivandsky.puzzlebox.domain.di.domainModule
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FeedTransport
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.LinkedInEdition
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.games.queens.feed.LinkedInQueensDailySource
import com.ivandsky.puzzlebox.games.queens.queensModule
import com.ivandsky.puzzlebox.games.sudoku.feed.LinkedInMiniSudokuDailySource
import com.ivandsky.puzzlebox.games.sudoku.miniSudokuModule
import com.ivandsky.puzzlebox.games.tango.feed.LinkedInTangoDailySource
import com.ivandsky.puzzlebox.games.tango.tangoModule
import com.ivandsky.puzzlebox.games.zip.feed.LinkedInZipDailySource
import com.ivandsky.puzzlebox.games.zip.zipModule
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.network.LINKEDIN_THROTTLE_COOLDOWN
import com.ivandsky.puzzlebox.network.linkedInFeedTransport
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.network.FakeFeedTransport
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The past-day contract of the four LinkedIn games over the REAL data path: production
 * `DailyRepository` and `LoadPastDay` over in-memory Room, each game's real source and trust
 * gate reading its committed games-by-id fixtures (shared from the game module's test
 * resources), through [FakeFeedTransport] — or, where the wire itself is the subject (the
 * session handshake, the A17 cooldown), the production LinkedIn transport over a [MockWebServer].
 *
 * Every day here is an edition before today's whatever the date the suite runs on (#1 and #2),
 * since the sources bound today by the wall clock (A4).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class LinkedInArchiveE2ETest {

    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
        .setQueryExecutor(RoomQueryThread.executor)
        .build()

    /** The one live graph: Koin caches a module's singles in the shared module object, so two live apps would share sources. */
    private var live: KoinApplication? = null
    private val servers = mutableListOf<MockWebServer>()

    @After
    fun tearDown() {
        live?.close()
        servers.forEach(MockWebServer::shutdown)
        db.close()
    }

    @Test
    fun `SC2 a never-seen past day costs one games-by-id call, stores one row, and plays again offline in a new graph`() =
        forEachGame { game ->
            val transport = FakeFeedTransport().apply { respond(game.byId(1), FetchResult.Ok(game.fixture(1))) }
            val first = graph(transport)
            first.assertPlays(game, first.open(game, game.day(1)))
            assertEquals("one games-by-id call", listOf(FakeFeedTransport.keyOf(game.byId(1))), transport.requests)
            assertEquals("one row, numbered by its edition", listOf(game.day(1) to 1), first.rows(game))
            first.assertPlays(game, first.open(game, game.day(1)))
            assertEquals("the reopen reads the cache", 1, transport.requests.size)

            // Process death: a new graph over the same database, with the network gone.
            val dead = FakeFeedTransport()
            val relaunched = graph(dead)
            relaunched.assertPlays(game, relaunched.open(game, game.day(1)))
            assertEquals("the offline reopen sends nothing", emptyList<String>(), dead.requests)
        }

    @Test
    fun `SC3 a body whose entityUrn names another edition is untrusted and stores nothing`() = forEachGame { game ->
        val transport = FakeFeedTransport().apply { respond(game.byId(2), FetchResult.Ok(game.fixture(1))) }
        val graph = graph(transport)
        assertFailed<FetchError.Untrusted>(graph.open(game, game.day(2)))
        assertEquals("nothing stored", emptyList<Pair<LocalDate, Int?>>(), graph.rows(game))
    }

    @Test
    fun `SC7 a day after today is never requested`() = forEachGame { game ->
        val transport = FakeFeedTransport()
        val graph = graph(transport)
        val today = graph.today(game)
        assertEquals("the archive ends at today", game.edition.day1..today, graph.daily.archiveRange(game.id))
        assertEquals(PastDayOutcome.OutOfRange, graph.open(game, today.plusDays(1)))
        assertEquals("no request for tomorrow", emptyList<String>(), transport.requests)
    }

    @Test
    fun `SC5 an offline past day needs a connection, and Tango never falls back to the seed`() = forEachGame { game ->
        val transport = FakeFeedTransport()
        val graph = graph(transport)
        // Failed, not Ready: for Tango this is also "never the bundled seed".
        assertFailed<FetchError.Unavailable>(graph.open(game, game.day(1)))
        assertEquals("the by-id request was tried once", listOf(FakeFeedTransport.keyOf(game.byId(1))), transport.requests)
        assertEquals("nothing stored", emptyList<Pair<LocalDate, Int?>>(), graph.rows(game))
    }

    @Test
    fun `SC2 on the wire a fresh session's past day sends one page GET and one games-by-id GET, then reads the cache`() =
        forEachGame { game ->
            val wire = Wire(game)
            val graph = graph(wire.transport)
            graph.assertPlays(game, graph.open(game, game.day(1)))
            assertEquals(FRESH_SESSION_WALK, wire.rungs)
            graph.assertPlays(game, graph.open(game, game.day(1)))
            assertEquals("the reopen reads the cache", FRESH_SESSION_WALK, wire.rungs)
        }

    @Test
    fun `SC8 Retry during the throttle cooldown sends no request`() = forEachGame { game ->
        val wire = Wire(game).apply { throttled = true }
        val graph = graph(wire.transport)
        assertFailed<FetchError.Throttled>(graph.open(game, game.day(1)))
        val sent = wire.rungs.size
        assertTrue("the throttled walk reached LinkedIn", sent > 0)

        wire.throttled = false
        wire.now = wire.now.plusSeconds(1)
        assertFailed<FetchError.Throttled>(graph.open(game, game.day(1)))
        assertEquals("Retry inside the cooldown is answered locally", sent, wire.rungs.size)

        wire.now = wire.now.plus(LINKEDIN_THROTTLE_COOLDOWN)
        graph.assertPlays(game, graph.open(game, game.day(1)))
        assertEquals("after the cooldown the day is fetched", FRESH_SESSION_WALK, wire.rungs.drop(sent))
    }

    /** One LinkedIn game: its id, its by-number edition, and its committed by-id fixture names. */
    private class LinkedInGame(val id: GameId, val edition: LinkedInEdition, private val fixturePrefix: String) {
        fun day(number: Int): LocalDate = edition.day1.plusDays(number - 1L)
        fun byId(number: Int) = edition.request(number)
        fun fixture(number: Int): String =
            checkNotNull(javaClass.classLoader?.getResource("fixtures/$fixturePrefix$number.json")) {
                "no by-id fixture #$number for ${id.value}"
            }.readText()
    }

    /** The real data graph of one launch over the shared [db]: data, domain and the four LinkedIn games; ends the last launch. */
    private inner class Graph(transport: FeedTransport) {
        private val app = run {
            live?.close()
            koinApplication {
                androidContext(RuntimeEnvironment.getApplication())
                modules(
                    commonModule, dataModule, unfilteredOwnerModule, domainModule, zipModule, queensModule, tangoModule, miniSudokuModule,
                    module {
                        single { transport }
                        single { db }
                        single { GameRegistry(getAll<GamePlugin<*, *, *>>()) }
                        single<ReleaseFlags> { FakeReleaseFlags() }
                    },
                )
            }.also { live = it }
        }
        val daily: DailyRepository get() = app.koin.get()
        val registry: GameRegistry get() = app.koin.get()

        fun today(game: LinkedInGame): LocalDate = checkNotNull(app.koin.get<GameCalendar>().today(game.id))
        suspend fun open(game: LinkedInGame, day: LocalDate): PastDayOutcome = app.koin.get<LoadPastDay>()(game.id, day, null)
        suspend fun rows(game: LinkedInGame) = daily.observeHistory(game.id).first().map { it.day to it.puzzleNumber }
    }

    private fun graph(transport: FeedTransport) = Graph(transport)

    /**
     * The production LinkedIn transport with its clock at [now], over a local server that
     * answers as LinkedIn does an anonymous session (F1): the stream is data-less and sets no
     * cookie; the plain page GET sets `JSESSIONID`; games-by-id refuses with 403 `CSRF check
     * failed.` unless `csrf-token` is that session, and otherwise answers the game's #1
     * fixture; while [throttled], every request gets LinkedIn's throttle signature.
     */
    private inner class Wire(game: LinkedInGame) {
        @Volatile var throttled = false
        @Volatile var now: Instant = Instant.parse("2026-01-01T12:00:00Z")
        private val sent = java.util.concurrent.CopyOnWriteArrayList<String>()

        /** Each request's rung, oldest first: [STREAM], [PAGE] or [BY_ID]. */
        val rungs: List<String> get() = sent.toList()

        private val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val rung = when {
                        checkNotNull(request.path).startsWith("/voyager/api/graphql?") -> BY_ID
                        request.getHeader("x-li-rsc-stream") != null -> STREAM
                        else -> PAGE
                    }.also(sent::add)
                    return when {
                        throttled -> MockResponse().addHeader("location", "https://www.linkedin.com/games/")
                        rung == STREAM -> MockResponse().setBody("2:\"\$Sreact.fragment\"\n5:I[0]")
                        rung == PAGE -> MockResponse().setBody("<html></html>").addHeader("Set-Cookie", "JSESSIONID=$SESSION; Path=/")
                        request.getHeader("csrf-token") != SESSION -> MockResponse().setResponseCode(403).setBody("CSRF check failed.")
                        else -> MockResponse().setBody(game.fixture(1))
                    }
                }
            }
            start()
        }.also(servers::add)

        private val clock = object : Clock() {
            override fun instant(): Instant = now
            override fun getZone(): ZoneId = ZoneId.of("UTC")
            override fun withZone(zone: ZoneId): Clock = this
        }

        val transport: FeedTransport = linkedInFeedTransport(server.url("/").toString(), Json { ignoreUnknownKeys = true }, clock)
    }

    private fun forEachGame(block: suspend (LinkedInGame) -> Unit) = runBlocking { GAMES.forEach { block(it) } }

    /** Ready, and the game's own session codec reads the session it built: the day plays. */
    private fun Graph.assertPlays(game: LinkedInGame, outcome: PastDayOutcome) {
        val ready = outcome as? PastDayOutcome.Ready ?: throw AssertionError("${game.id.value}: $outcome, expected Ready")
        val blob = checkNotNull(SessionBlob.decode(ready.session)) { "${game.id.value}: no session envelope" }
        assertEquals(game.id, blob.game)
        val decoded = checkNotNull(registry[game.id]).session.deserialize(blob.session)
        assertTrue("${game.id.value}: replay session does not decode: $decoded", decoded is DecodeResult.Ok)
    }

    private inline fun <reified E : FetchError> assertFailed(outcome: PastDayOutcome) {
        val error = (outcome as? PastDayOutcome.Failed)?.error
        assertTrue("expected Failed(${E::class.simpleName}), got $outcome", error is E)
    }

    private companion object {
        val GAMES = listOf(
            LinkedInGame(GameId("zip"), LinkedInZipDailySource.EDITION, "02-byid-gt6-"),
            LinkedInGame(GameId("queens"), LinkedInQueensDailySource.EDITION, "02-byid-gt3-"),
            LinkedInGame(GameId("tango"), LinkedInTangoDailySource.EDITION, "linkedin-tango-byid-"),
            LinkedInGame(GameId("minisudoku"), LinkedInMiniSudokuDailySource.EDITION, "02-byid-gt7-"),
        )

        /**
         * A fresh session's past-day walk (SC2, F1): the page GET that hands out the session,
         * then the signed by-id GET. The stream is skipped; no by-id GET is ever re-sent.
         */
        val FRESH_SESSION_WALK = listOf(PAGE, BY_ID)
        const val SESSION = "ajax:2026"
        const val STREAM = "stream"
        const val PAGE = "page"
        const val BY_ID = "games-by-id"
    }
}
