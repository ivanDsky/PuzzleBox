package com.ivandsky.puzzlebox.domain.insights.rankhistory

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.FinalBoardStore
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.rankhistory.RankHistoryStore
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendBoards
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendUids
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendsOverall
import com.ivandsky.puzzlebox.domain.ranks.ObserveOverall
import com.ivandsky.puzzlebox.domain.ranks.ObserveRankWindow
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeFinalBoardStore
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeRankHistoryStore
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rank history filler over counting fakes: what it reads from the cloud, what it keeps, and when it does neither.
 * Today is Thursday 2026-11-19, so the newest final week starts Monday 2026-11-09 (its last board, Sunday the 15th, is
 * older than three days) and the week of the 16th is still open.
 */
class SyncRankHistoryTest {

    private val clock = Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 11, 19)
    private val weekTimeout = 10.seconds
    private val zip = GameId("zip")
    private val tango = GameId("tango")
    private val registry = TestGames.registry(listOf(zip, tango))
    private val me = Identity("u1", "Ann", null)
    private val identity = FakeIdentityService(initial = me)
    private val matches = FakeMatchRepository().apply { signedInUid = me.uid }

    private val newestFinalWeek = LocalDate.of(2026, 11, 9)

    /** Mondays of twelve final weeks, newest first. */
    private val finalWeeks = generateSequence(newestFinalWeek) { it.minusWeeks(1) }.take(12).toList()

    private val leaderboard = FakeLeaderboardRepository()
    private val reads = CountingLeaderboardRepository(leaderboard)
    private val history = mutableListOf<CompletionRecord>()

    /** Wraps [inner] to log every one-shot board read, and to hold chosen boards back until their gate opens. */
    private class CountingLeaderboardRepository(private val inner: FakeLeaderboardRepository) : LeaderboardRepository by inner {
        val fetched = mutableListOf<LeaderboardRef>()
        val gates = mutableMapOf<LeaderboardRef, CompletableDeferred<Unit>>()

        override suspend fun fetch(board: LeaderboardRef, order: RankOrder): BoardPage? {
            fetched += board
            gates[board]?.await()
            return inner.fetch(board, order)
        }
    }

    private fun LeaderboardRef.week(): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** The weeks the reads touched, in the order they began, each run of one week counted once. */
    private fun readWeeks(): List<LocalDate> =
        reads.fetched.map { it.week() }.let { weeks -> weeks.filterIndexed { i, week -> i == 0 || weeks[i - 1] != week } }

    /** The viewer solved [game] on [day] in this app; [ranked] is who stands on that board, first place first. */
    private fun played(
        game: GameId,
        day: LocalDate,
        ranked: List<String> = listOf("maya", me.uid),
        source: CompletionSource = CompletionSource.App,
    ) {
        history += CompletionRecord(
            puzzleRef = TestGames.Ref(game, PuzzleKey.Daily(day, 1)),
            time = 41_000L,
            hints = 0,
            stats = RecordStats(schemaVersion = 1, blob = "{}"),
            source = source,
        )
        val board = LeaderboardRef(game.value, day)
        leaderboard.setEntries(
            board,
            ranked.mapIndexed { place, uid -> LeaderboardEntry(uid, uid, "${game.value}/$day/1", 40_000L + place * 1_000L, 0, 1_700_000_000_000) },
        )
    }

    private suspend fun befriendMaya() {
        val code = checkNotNull(ShareCode.parse("AAAAA2"))
        matches.create(MatchDraft(code, 24, Player(me.uid, me.displayName, null), null, listOf(MatchLeg(LegRef.Daily(zip, today, 1), null)), true, null))
        matches.signedInUid = "maya"
        matches.claim(code, Player("maya", "maya", null))
        matches.signedInUid = me.uid
    }

    private fun filler(
        store: RankHistoryStore,
        finalBoards: FinalBoardStore = FakeFinalBoardStore(),
    ): SyncRankHistory {
        val friendUids = ObserveFriendUids(matches, identity)
        return SyncRankHistory(
            identity = identity,
            observeFriendUids = friendUids,
            observeGameHistory = ObserveGameHistory(registry, FakeDailyRepository(), FakeCompletionRepository(history.toList()), flowOf(emptyMap())),
            store = store,
            friendsOverall = ObserveFriendsOverall(
                ObserveRankWindow(identity, reads, finalBoards, registry, FakeGameCalendar(today), clock),
                ObserveFriendBoards(friendUids, identity, null),
                ObserveOverall(identity, FakeCompletionRepository()),
            ),
            clock = clock,
            weekReadTimeout = weekTimeout,
        )
    }

    /**
     * Starts the filler in the background scope and runs everything due now. The filler never returns on its own, and
     * `advanceUntilIdle` leaves background work alone, so only `runCurrent` moves it.
     */
    private fun TestScope.run(filler: SyncRankHistory): Job = backgroundScope.launch { filler() }.also { testScheduler.runCurrent() }

    private suspend fun RankHistoryStore.kept(game: GameId, owner: String = me.uid) =
        weeks(owner, game, LocalDate.EPOCH..LocalDate.of(9999, 12, 31))

    @Test
    fun `a cold device reads the last eight final weeks only, newest first`() = runTest {
        befriendMaya()
        finalWeeks.forEach { played(zip, it) }
        val store = FakeRankHistoryStore()

        run(filler(store))

        val expected = finalWeeks.take(8)
        assertEquals(expected, readWeeks())
        assertEquals(8 * 7, reads.fetched.size)
        assertEquals(expected.sorted(), store.kept(zip).map { it.weekStart })
    }

    @Test
    fun `a device that has kept weeks reads every unkept week since the oldest, however long the gap`() = runTest {
        befriendMaya()
        finalWeeks.forEach { played(zip, it) }
        val oldestKept = finalWeeks[10]
        val keptBefore = RankHistoryStore.Week(me.uid, zip, oldestKept, pointsPerDay = 12.5, friendCount = 4)
        val store = FakeRankHistoryStore(listOf(keptBefore))

        run(filler(store))

        // Ten weeks have passed since the kept one: all ten are read, newest first. The week before the kept one is
        // older than anything this device kept and outside the last eight, so it is not refilled.
        assertEquals(finalWeeks.take(10), readWeeks())
        assertEquals(10 * 7, reads.fetched.size)
        assertEquals(finalWeeks.take(11).sorted(), store.kept(zip).map { it.weekStart })
        assertEquals(keptBefore, store.kept(zip).single { it.weekStart == oldestKept })
    }

    @Test
    fun `a run after a cold refill does not reach back past the last eight weeks`() = runTest {
        befriendMaya()
        finalWeeks.forEach { played(zip, it) }
        val store = FakeRankHistoryStore()
        run(filler(store)).cancel()
        val readsOfColdRun = reads.fetched.size

        run(filler(store, FakeFinalBoardStore()))

        assertEquals(8 * 7, readsOfColdRun)
        assertEquals(readsOfColdRun, reads.fetched.size)
        assertEquals(finalWeeks.take(8).sorted(), store.kept(zip).map { it.weekStart })
    }

    @Test
    fun `a week already kept is not read and stays exactly as it was`() = runTest {
        befriendMaya()
        played(zip, newestFinalWeek)
        played(zip, newestFinalWeek.minusWeeks(1))
        val keptBefore = RankHistoryStore.Week(me.uid, zip, newestFinalWeek, pointsPerDay = 12.5, friendCount = 4)
        val store = FakeRankHistoryStore(listOf(keptBefore))

        run(filler(store))

        assertEquals(listOf(newestFinalWeek.minusWeeks(1)), readWeeks())
        assertEquals(keptBefore, store.kept(zip).single { it.weekStart == newestFinalWeek })
        assertEquals(2, store.kept(zip).size)
    }

    @Test
    fun `a run after a complete run reads nothing`() = runTest {
        befriendMaya()
        finalWeeks.take(3).forEach { played(zip, it) }
        val store = FakeRankHistoryStore()
        run(filler(store)).cancel()
        val readsOfFirstRun = reads.fetched.size

        // A fresh final-board store: only the kept weeks can stop the second run from reading again.
        run(filler(store, FakeFinalBoardStore()))

        assertEquals(3 * 7, readsOfFirstRun)
        assertEquals(readsOfFirstRun, reads.fetched.size)
        assertEquals(3, store.kept(zip).size)
    }

    @Test
    fun `another account's kept week does not count as kept`() = runTest {
        befriendMaya()
        played(zip, newestFinalWeek)
        val store = FakeRankHistoryStore(listOf(RankHistoryStore.Week("u2", zip, newestFinalWeek, 80.0, 2)))

        run(filler(store))

        assertEquals(listOf(newestFinalWeek), store.kept(zip).map { it.weekStart })
        assertEquals(listOf(newestFinalWeek), readWeeks())
    }

    @Test
    fun `a week still loading is not kept until every board of it has loaded`() = runTest {
        befriendMaya()
        played(zip, newestFinalWeek)
        val lastBoard = LeaderboardRef(zip.value, newestFinalWeek.plusDays(6))
        val gate = CompletableDeferred<Unit>().also { reads.gates[lastBoard] = it }
        val store = FakeRankHistoryStore()

        run(filler(store))

        assertTrue(lastBoard in reads.fetched)
        assertEquals(emptyList<RankHistoryStore.Week>(), store.kept(zip))

        gate.complete(Unit)
        testScheduler.runCurrent()

        assertEquals(listOf(newestFinalWeek), store.kept(zip).map { it.weekStart })
    }

    @Test
    fun `an offline read keeps nothing and the next run tries the week again`() = runTest {
        befriendMaya()
        played(zip, newestFinalWeek)
        leaderboard.readsAvailable = false
        val store = FakeRankHistoryStore()
        run(filler(store)).cancel()

        assertEquals(emptyList<RankHistoryStore.Week>(), store.kept(zip))

        leaderboard.readsAvailable = true
        run(filler(store, FakeFinalBoardStore()))

        assertEquals(listOf(newestFinalWeek), store.kept(zip).map { it.weekStart })
    }

    @Test
    fun `a week that never loads is given up on after the timeout and the next week is read`() = runTest {
        befriendMaya()
        played(zip, newestFinalWeek)
        played(zip, newestFinalWeek.minusWeeks(1))
        reads.gates[LeaderboardRef(zip.value, newestFinalWeek.plusDays(6))] = CompletableDeferred()
        val store = FakeRankHistoryStore()
        val first = run(filler(store))

        assertEquals(listOf(newestFinalWeek), readWeeks())

        testScheduler.advanceTimeBy(weekTimeout - 1.milliseconds)
        testScheduler.runCurrent()
        assertEquals(listOf(newestFinalWeek), readWeeks())

        testScheduler.advanceTimeBy(1.milliseconds)
        testScheduler.runCurrent()
        assertEquals(listOf(newestFinalWeek, newestFinalWeek.minusWeeks(1)), readWeeks())
        assertEquals(listOf(newestFinalWeek.minusWeeks(1)), store.kept(zip).map { it.weekStart })

        // The week it gave up on was not kept, so the next run reads it again.
        first.cancel()
        reads.gates.clear()
        run(filler(store, FakeFinalBoardStore()))

        assertEquals(listOf(newestFinalWeek.minusWeeks(1), newestFinalWeek), store.kept(zip).map { it.weekStart })
    }

    @Test
    fun `a week nobody played on the boards is not kept`() = runTest {
        befriendMaya()
        played(zip, newestFinalWeek, ranked = emptyList())
        val store = FakeRankHistoryStore()

        run(filler(store))

        assertEquals(emptyList<RankHistoryStore.Week>(), store.kept(zip))
    }

    @Test
    fun `a week where the viewer is not on any board is not kept`() = runTest {
        befriendMaya()
        played(zip, newestFinalWeek, ranked = listOf("maya"))
        val store = FakeRankHistoryStore()

        run(filler(store))

        assertEquals(emptyList<RankHistoryStore.Week>(), store.kept(zip))
    }

    @Test
    fun `a solve imported from elsewhere makes no week due`() = runTest {
        befriendMaya()
        played(zip, newestFinalWeek, source = CompletionSource.LinkedIn)
        val store = FakeRankHistoryStore()

        run(filler(store))

        assertEquals(emptyList<LeaderboardRef>(), reads.fetched)
        assertEquals(emptyList<RankHistoryStore.Week>(), store.kept(zip))
    }

    @Test
    fun `a week that is still open is not read`() = runTest {
        befriendMaya()
        played(zip, LocalDate.of(2026, 11, 16))
        played(zip, newestFinalWeek)
        val store = FakeRankHistoryStore()

        run(filler(store))

        assertEquals(listOf(newestFinalWeek), readWeeks())
        assertEquals(emptyList<LeaderboardRef>(), leaderboard.observedBoards)
        assertEquals(listOf(newestFinalWeek), store.kept(zip).map { it.weekStart })
    }

    @Test
    fun `a game is read only for the weeks it was played`() = runTest {
        befriendMaya()
        played(zip, newestFinalWeek)
        played(tango, newestFinalWeek.minusWeeks(2))
        val store = FakeRankHistoryStore()

        run(filler(store))

        assertEquals(listOf(newestFinalWeek), store.kept(zip).map { it.weekStart })
        assertEquals(listOf(newestFinalWeek.minusWeeks(2)), store.kept(tango).map { it.weekStart })
        assertEquals(setOf("zip" to newestFinalWeek, "tango" to newestFinalWeek.minusWeeks(2)), reads.fetched.map { it.gameId to it.week() }.toSet())
    }

    @Test
    fun `a kept week holds the points per played day and how many friends stood`() = runTest {
        befriendMaya()
        // Monday second behind Maya (50), Tuesday first ahead of her (100): 150 over two played days.
        played(zip, newestFinalWeek)
        played(zip, newestFinalWeek.plusDays(1), ranked = listOf(me.uid, "maya"))
        val store = FakeRankHistoryStore()

        run(filler(store))

        assertEquals(
            listOf(RankHistoryStore.Week(me.uid, zip, newestFinalWeek, pointsPerDay = 75.0, friendCount = 1)),
            store.kept(zip),
        )
    }

    @Test
    fun `signed out it reads nothing`() = runTest {
        befriendMaya()
        played(zip, newestFinalWeek)
        identity.currentIdentity = null
        val store = FakeRankHistoryStore()

        run(filler(store))

        assertEquals(emptyList<LeaderboardRef>(), reads.fetched)
        assertEquals(emptyList<RankHistoryStore.Week>(), store.kept(zip))
    }

    @Test
    fun `without friends it reads nothing, and starts when the first friend arrives`() = runTest {
        played(zip, newestFinalWeek)
        val store = FakeRankHistoryStore()

        run(filler(store))

        assertEquals(emptyList<LeaderboardRef>(), reads.fetched)

        befriendMaya()
        testScheduler.runCurrent()

        assertEquals(listOf(newestFinalWeek), store.kept(zip).map { it.weekStart })
    }
}
