package com.ivandsky.puzzlebox.play.domain.nextthing

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetNextRollover
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.domain.ObserveFriendPuzzles
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.moments.Moment
import com.ivandsky.puzzlebox.domain.nextthing.NextThing
import com.ivandsky.puzzlebox.domain.nextthing.ResultNextThing
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendUids
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeHiddenPuzzles
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeVisibilityRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Result's moments and next thing for one solve (L1, L3, L4), read from fakes on virtual time. Today's puzzle day
 * is [TODAY] (the test games' zone is UTC). The scene: both games' dailies are today's set; the player ("me") has
 * solved beta today and alpha yesterday in 85 s without hints; friends ann (30 s) and bea (70 s) and a stranger zed
 * (20 s) are on alpha's board.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveResultNextThingTest {

    private val me = Identity("me", "me", null)
    private val registry = TestGames.registry(listOf(alpha, beta))
    private val clock = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC)
    private val rollover = Instant.parse("2026-11-17T00:00:00Z")
    private val board = LeaderboardRef(alpha.value, TODAY)

    private val identity = FakeIdentityService(initial = me)
    private val matches = FakeMatchRepository().apply { signedInUid = me.uid }
    private val completions = FakeCompletionRepository()
    private val calendar = FakeGameCalendar(TODAY)
    private val saved = FakeSavedPlayRepository()
    private val received = FakeReceivedPuzzleRepository()
    private val visibility = FakeVisibilityRepository()
    private val leaderboard = FakeLeaderboardRepository()
    private val daily = FakeDailyRepository().apply { listOf(alpha, beta).forEach { setToday(it, TestGames.puzzle(it)) } }
    private val failures = mutableListOf<Throwable>()

    private var friendCount = 0

    private fun record(game: GameId, daysAgo: Long, timeMs: Long, hints: Int = 0) = CompletionRecord(
        puzzleRef = Ref(game, PuzzleKey.Daily(TODAY.minusDays(daysAgo), 100)),
        time = timeMs,
        hints = hints,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
        isRecord = true,
    )

    private fun entry(uid: String, timeMs: Long) =
        LeaderboardEntry(uid, uid, "${alpha.value}/$TODAY/100", timeMs, 0, 1L)

    private suspend fun befriend(uid: String) {
        val code = checkNotNull(ShareCode.parse("AAAAA${friendCount++ + 2}"))
        matches.create(MatchDraft(code, 24, Player(me.uid, "me", null), null, listOf(MatchLeg(LegRef.Daily(alpha, TODAY, 100), null)), true, null))
        matches.signedInUid = uid
        matches.claim(code, Player(uid, uid, null))
        matches.signedInUid = me.uid
    }

    /** The scene above; [meOnBoard] puts the player's own entry on the board, as when the submit has landed. */
    private suspend fun scene(meOnBoard: Long? = null, betaSolved: Boolean = true) {
        completions.setRecords(listOfNotNull(record(beta, 0, 70_000).takeIf { betaSolved }, record(alpha, 1, 85_000)))
        befriend("ann")
        befriend("bea")
        leaderboard.setEntries(
            board,
            listOfNotNull(entry("zed", 20_000), entry("ann", 30_000), entry("bea", 70_000), meOnBoard?.let { entry("me", it) }),
        )
        visibility.putTier("ann", VisibilityTier.Duel)
    }

    private fun observe(
        leaderboardRepo: LeaderboardRepository = leaderboard,
        timeout: Duration = 2.seconds,
        showDayTotal: Boolean = false,
        registry: GameRegistry = this.registry,
    ) = ObserveResultNextThing(
        registry = registry,
        identity = identity,
        history = ObserveGameHistory(registry, daily, completions),
        todaySet = ObserveTodaySet(registry, saved, completions, calendar, daily, SyncTodayPuzzles(registry, daily, calendar)),
        friendPuzzles = ObserveFriendPuzzles(received, saved, completions, registry, FakeHiddenPuzzles()),
        friendUids = ObserveFriendUids(matches, identity),
        leaderboard = leaderboardRepo,
        visibility = visibility,
        computeStreak = ComputeStreak(GetPuzzleDay(registry, clock)),
        puzzleDay = GetPuzzleDay(registry, clock),
        nextRollover = GetNextRollover(registry, clock),
        communityPuzzles = ListCommunityPuzzles(FakeSharedPuzzleRepository(), FakeHiddenPuzzles(), identity) { 0L },
        timeout = timeout,
        failures = { _, _, cause -> failures += cause },
        showDayTotal = showDayTotal,
    )

    private data class Seen(val at: Long, val value: ResultNextThing)

    private suspend fun TestScope.solve(
        timeMs: Long,
        hints: Int = 0,
        newBest: Boolean = true,
        playAgain: Boolean = false,
        practice: Boolean = false,
        use: ObserveResultNextThing = observe(),
    ): List<Seen> {
        val seen = mutableListOf<Seen>()
        use(alpha, TODAY, BestScore(timeMs, hints), playAgain, practice, newBest).collect { seen += Seen(currentTime, it) }
        return seen
    }

    private val passedBea = Moment.PassedFriend("bea", "bea")
    private val challenge = NextThing.ChallengeFriend("ann", "ann")

    @Test
    fun `with the day total on, the full set sums this solve and the other solved games' times and hints`() = runTest {
        scene()
        completions.setRecords(listOf(record(beta, 0, 70_000, hints = 2), record(alpha, 1, 85_000)))

        val seen = solve(timeMs = 60_000, hints = 1, use = observe(showDayTotal = true))

        val total = Moment.FullSetDone.DayTotal(timeMs = 130_000, hints = 3)
        assertEquals(Moment.FullSetDone(total), seen.first().value.moments.last())
    }

    @Test
    fun `a hint-free personal best with a friend above emits the local moments first, then the friend-board moment and a challenge`() = runTest {
        scene()

        val seen = solve(timeMs = 60_000)

        assertEquals(ResultNextThing(listOf(Moment.PersonalBest, Moment.FullSetDone()), null), seen.first().value)
        assertEquals(ResultNextThing(listOf(Moment.PersonalBest, passedBea, Moment.FullSetDone()), challenge, challenge), seen.last().value)
        assertTrue(seen.dropLast(1).none { it.value.nextThing == challenge && passedBea !in it.value.moments })
        assertEquals("the chain's rung and the Challenge row share one tier read", listOf("ann"), visibility.lookupCalls)
    }

    @Test
    fun `a game the registry keeps off every surface is never offered as the next daily`() = runTest {
        // Mirrors the old "Next skips a registered game the store cannot play": beta ships but is unlisted, so it is
        // not in today's set, and the chain skips to gamma. A bound plugin with no play UI cannot reach here at all:
        // the app's registry refuses to resolve (requireEveryGamePlayable).
        val gamma = GameId("gamma")
        daily.setToday(gamma, TestGames.puzzle(gamma))
        scene(betaSolved = false)
        val games = GameRegistry(listOf(alpha, beta, gamma).map(TestGames::plugin), unlisted = setOf(beta))

        val seen = solve(timeMs = 60_000, use = observe(registry = games))

        assertEquals(NextThing.NextDaily(gamma), seen.last().value.nextThing)
    }

    @Test
    fun `the friend to challenge is offered beside the next daily, which never waits for it`() = runTest {
        scene(betaSolved = false)

        val seen = solve(timeMs = 60_000)

        assertEquals(ResultNextThing(listOf(Moment.PersonalBest, passedBea), NextThing.NextDaily(beta), challenge), seen.last().value)
        assertEquals(listOf("ann"), visibility.lookupCalls)
    }

    @Test
    fun `the friend-board moment is the same whether the solve's own entry has landed on the board or not`() = runTest {
        scene(meOnBoard = 60_000)

        val last = solve(timeMs = 60_000).last().value

        assertEquals(ResultNextThing(listOf(Moment.PersonalBest, passedBea, Moment.FullSetDone()), challenge, challenge), last)
    }

    @Test
    fun `an entry on the board that is not this solve leaves the friends where they were and passes nobody`() = runTest {
        scene(meOnBoard = 50_000)

        val last = solve(timeMs = 60_000).last().value

        assertEquals(ResultNextThing(listOf(Moment.PersonalBest, Moment.FullSetDone()), challenge, challenge), last)
    }

    @Test
    fun `a friend whose tier is below duel is never offered a challenge`() = runTest {
        scene()
        visibility.putTier("ann", VisibilityTier.Board)

        val seen = solve(timeMs = 60_000)

        assertTrue(seen.none { it.value.nextThing is NextThing.ChallengeFriend || it.value.challenge != null })
        assertEquals(NextThing.DoneForToday(rollover), seen.last().value.nextThing)
        assertTrue("the friend-board moment is not gated on the tier", passedBea in seen.last().value.moments)
    }

    @Test
    fun `a failed tier read is never a challenge, and the tier was read once`() = runTest {
        scene()
        visibility.failingUids += "ann"

        val seen = solve(timeMs = 60_000)

        assertTrue(seen.none { it.value.nextThing is NextThing.ChallengeFriend })
        assertEquals(NextThing.DoneForToday(rollover), seen.last().value.nextThing)
        assertEquals(listOf("ann"), visibility.lookupCalls)
    }

    @Test
    fun `a play again emits neither moments nor a next thing and reads nothing from the network`() = runTest {
        scene()

        val seen = solve(timeMs = 60_000, playAgain = true)

        assertEquals(listOf(ResultNextThing()), seen.map { it.value })
        assertEquals(0, leaderboard.fetchCalls)
        assertEquals(emptyList<String>(), visibility.lookupCalls)
    }

    @Test
    fun `a practice solve emits neither moments nor a next thing and reads nothing from the network`() = runTest {
        scene()

        val seen = solve(timeMs = 60_000, practice = true)

        assertEquals(listOf(ResultNextThing()), seen.map { it.value })
        assertEquals(0, leaderboard.fetchCalls)
        assertEquals(emptyList<String>(), visibility.lookupCalls)
    }

    @Test
    fun `a board that is slow to answer holds back nothing local and the chain falls through at its own timeout`() = runTest {
        scene()
        val slow = object : LeaderboardRepository by leaderboard {
            override suspend fun fetch(board: LeaderboardRef, order: RankOrder): BoardPage? {
                delay(10.seconds)
                return leaderboard.fetch(board, order)
            }
        }

        val seen = solve(timeMs = 60_000, use = observe(slow))

        assertEquals(listOf(Seen(0, ResultNextThing(listOf(Moment.PersonalBest, Moment.FullSetDone()), null))), seen.take(1))
        assertEquals(Seen(2_000, ResultNextThing(listOf(Moment.PersonalBest, Moment.FullSetDone()), NextThing.DoneForToday(rollover))), seen.last())
        assertEquals(2_000, currentTime)
    }

    @Test
    fun `the next daily is offered at once however slow the board is`() = runTest {
        scene(betaSolved = false)
        val slow = object : LeaderboardRepository by leaderboard {
            override suspend fun fetch(board: LeaderboardRef, order: RankOrder): BoardPage? {
                delay(10.seconds)
                return leaderboard.fetch(board, order)
            }
        }

        val seen = solve(timeMs = 60_000, use = observe(slow))

        assertEquals(Seen(0, ResultNextThing(listOf(Moment.PersonalBest), NextThing.NextDaily(beta))), seen.last { it.at == 0L })
        assertNull("a board that never answers offers nobody to challenge", seen.last().value.challenge)
    }

    @Test
    fun `a board that cannot be read leaves the local moments and no challenge`() = runTest {
        scene()
        leaderboard.readsAvailable = false

        val seen = solve(timeMs = 60_000)

        assertEquals(ResultNextThing(listOf(Moment.PersonalBest, Moment.FullSetDone()), NextThing.DoneForToday(rollover)), seen.last().value)
        assertEquals(emptyList<String>(), visibility.lookupCalls)
    }

    @Test
    fun `signed out the local moments come and nothing is read from the network`() = runTest {
        scene()
        identity.currentIdentity = null

        val seen = solve(timeMs = 60_000)

        assertEquals(ResultNextThing(listOf(Moment.PersonalBest, Moment.FullSetDone()), NextThing.DoneForToday(rollover)), seen.last().value)
        assertEquals(0, leaderboard.fetchCalls)
        assertEquals(emptyList<String>(), visibility.lookupCalls)
    }

    @Test
    fun `a streak counted with this solve is the same whether its record has landed or not`() = runTest {
        val earlier = (1L..6L).map { record(alpha, it, 100_000) }
        completions.setRecords(earlier)
        assertEquals(listOf(Moment.Streak(7)), solve(timeMs = 60_000, hints = 1, newBest = false).first().value.moments)

        completions.setRecords(earlier + record(alpha, 0, 60_000, hints = 1))
        assertEquals(listOf(Moment.Streak(7)), solve(timeMs = 60_000, hints = 1, newBest = false).first().value.moments)
    }

    @Test
    fun `the solve's own landed record is not an earlier best`() = runTest {
        completions.setRecords(listOf(record(alpha, 0, 80_000)))

        val moments = solve(timeMs = 80_000, newBest = false).first().value.moments

        assertEquals(listOf(Moment.FirstSubX(90_000)), moments)
    }

    @Test
    fun `an unsolved puzzle of the game from the last week is offered when nothing else is`() = runTest {
        scene()
        leaderboard.setEntries(board, emptyList())
        daily.addEntry(DailyEntry(alpha, TODAY.minusDays(2), 98, "v", fetchedAtMs = 1L))

        val last = solve(timeMs = 60_000).last().value

        assertEquals(NextThing.MissedDay(alpha, TODAY.minusDays(2)), last.nextThing)
    }

    @Test
    fun `a received friend puzzle never played is offered when nothing else is`() = runTest {
        scene()
        leaderboard.setEntries(board, emptyList())
        val code = checkNotNull(ShareCode.parse("K7Q2MX"))
        received.upsert(
            ReceivedPuzzle(
                code = code,
                gameId = alpha,
                variant = TestGames.variant(alpha),
                sharedFormatVersion = 1,
                puzzleBytes = byteArrayOf(TestGames.MAGIC),
                title = "Crown",
                authorName = "Bo",
                note = "",
                authorTimeMs = 100_000,
                authorHints = 0,
                authorUid = "author",
                openedAt = Instant.ofEpochMilli(1_700_000_000_000),
                unpublished = false,
                postState = PostState.Pending,
                postAttempts = 0,
                difficulty = null,
            ),
        )

        val last = solve(timeMs = 60_000).last().value

        assertEquals(NextThing.FriendPuzzle(code, alpha, "Bo"), last.nextThing)
        assertNull(failures.firstOrNull())
    }
}
