package com.ivandsky.puzzlebox.play.domain.nextthing

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.CommunityFilter
import com.ivandsky.puzzlebox.data.repository.CommunityListResult
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetNextRollover
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.domain.ObserveFriendPuzzles
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.nextthing.NextThing
import com.ivandsky.puzzlebox.domain.nextthing.ResultNextThing
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendUids
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
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
 * Result's community rung (L3, C2) through the real list use case over fakes: the player ("me") has finished
 * today's set and has no friends, no missed day and no friend puzzle, so the chain reaches the community rung.
 * "Now" is noon on [TODAY]; community puzzles are created hours before it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveResultCommunityNextThingTest {

    private val me = Identity("me", "me", null)
    private val registry = TestGames.registry(listOf(alpha, beta))
    private val clock = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC)
    private val now = clock.millis()
    private val rollover = Instant.parse("2026-11-17T00:00:00Z")

    private val identity = FakeIdentityService(initial = me)
    private val completions = FakeCompletionRepository()
    private val received = FakeReceivedPuzzleRepository()
    private val shared = FakeSharedPuzzleRepository().apply { clock = { now } }
    private val hidden = FakeHiddenPuzzles()
    private val failures = mutableListOf<Throwable>()
    private val daily = FakeDailyRepository().apply { listOf(alpha, beta).forEach { setToday(it, TestGames.puzzle(it)) } }

    private fun code(value: String) = checkNotNull(ShareCode.parse(value))

    private fun record(game: GameId) = CompletionRecord(
        puzzleRef = Ref(game, PuzzleKey.Daily(TODAY, 100)),
        time = 70_000,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
        isRecord = true,
    )

    private suspend fun publish(code: String, author: String, hoursAgo: Long, game: String = alpha.value, name: String = "Author") {
        shared.signedInUid = author
        shared.publishesToday = 0
        shared.publish(
            SharedPuzzleDoc(
                code = code(code), formatVersion = SharedPuzzleDoc.FORMAT_VERSION, gameId = game, variant = "$game/v",
                shareCodecVersion = 1, payload = byteArrayOf(1), title = "T$code", authorName = name, note = "",
                authorUid = author, authorTimeMs = 1_000L, authorHints = 0, createdAtMs = now - hoursAgo * HOUR,
                unpublished = false, visibility = SharedPuzzleDoc.Visibility.Public,
            ),
        )
    }

    /** A puzzle opened here and since taken down: it is received, but no friend rung offers it. */
    private suspend fun open(code: String) = received.upsert(
        ReceivedPuzzle(
            code = code(code), gameId = alpha, variant = TestGames.variant(alpha), sharedFormatVersion = 1,
            puzzleBytes = byteArrayOf(TestGames.MAGIC), title = "Opened", authorName = "Bo", note = "", authorTimeMs = 100_000,
            authorHints = 0, authorUid = "author", openedAt = Instant.ofEpochMilli(now), unpublished = true,
            postState = PostState.Pending, postAttempts = 0, difficulty = null,
        ),
    )

    private fun observe(
        repository: SharedPuzzleRepository = shared,
        timeout: Duration = 2.seconds,
    ) = ObserveResultNextThing(
        registry = registry,
        identity = identity,
        history = ObserveGameHistory(registry, daily, completions),
        todaySet = ObserveTodaySet(registry, FakeSavedPlayRepository(), completions, FakeGameCalendar(TODAY), daily, SyncTodayPuzzles(registry, daily, FakeGameCalendar(TODAY))),
        friendPuzzles = ObserveFriendPuzzles(received, FakeSavedPlayRepository(), completions, registry, hidden),
        friendUids = ObserveFriendUids(FakeMatchRepository().apply { signedInUid = me.uid }, identity),
        leaderboard = FakeLeaderboardRepository(),
        visibility = FakeVisibilityRepository(),
        computeStreak = ComputeStreak(GetPuzzleDay(registry, clock)),
        puzzleDay = GetPuzzleDay(registry, clock),
        nextRollover = GetNextRollover(registry, clock),
        communityPuzzles = ListCommunityPuzzles(repository, hidden, identity) { now },
        timeout = timeout,
        failures = { _, _, cause -> failures += cause },
    )

    private suspend fun TestScope.nextThing(
        use: ObserveResultNextThing = observe(),
        playAgain: Boolean = false,
        practice: Boolean = false,
    ): NextThing? {
        completions.setRecords(listOf(record(beta)))
        var last: ResultNextThing? = null
        use(alpha, TODAY, BestScore(60_000, 0), playAgain, practice, true).collect { last = it }
        return last?.nextThing
    }

    private fun community(code: String, name: String = "Author", game: GameId = alpha) =
        NextThing.CommunityPuzzle(code(code), game, name)

    @Test
    fun `the newest puzzle the player has not opened is offered`() = runTest {
        publish("AAAA22", "zed", hoursAgo = 5, name = "Old")
        publish("BBBB33", "zed", hoursAgo = 1, name = "Fresh")
        publish("CCCC44", "zed", hoursAgo = 3, name = "Middle")

        assertEquals(community("BBBB33", "Fresh"), nextThing())
        assertNull(failures.firstOrNull())
    }

    @Test
    fun `the player's own puzzle, a hidden one and an opened one are skipped`() = runTest {
        publish("AAAA22", "me", hoursAgo = 1)
        publish("BBBB33", "zed", hoursAgo = 2)
        publish("CCCC44", "zed", hoursAgo = 3)
        publish("DDDD55", "zed", hoursAgo = 4, name = "Next")
        hidden.add(code("BBBB33"))
        open("CCCC44")

        assertEquals(community("DDDD55", "Next"), nextThing())
    }

    @Test
    fun `a puzzle older than the window is not offered`() = runTest {
        publish("AAAA22", "zed", hoursAgo = 25)

        assertEquals(NextThing.DoneForToday(rollover), nextThing())
    }

    @Test
    fun `a puzzle of a game this build does not register is not offered`() = runTest {
        publish("AAAA22", "zed", hoursAgo = 1, game = "sudoku9")
        publish("BBBB33", "zed", hoursAgo = 2, name = "Known")

        assertEquals(community("BBBB33", "Known"), nextThing())
    }

    @Test
    fun `an empty list falls through to done for today`() = runTest {
        assertEquals(NextThing.DoneForToday(rollover), nextThing())
    }

    @Test
    fun `an offline list falls through to done for today`() = runTest {
        publish("AAAA22", "zed", hoursAgo = 1)
        shared.online = false

        assertEquals(NextThing.DoneForToday(rollover), nextThing())
    }

    @Test
    fun `a refused list falls through to done for today`() = runTest {
        publish("AAAA22", "zed", hoursAgo = 1)
        shared.listRefused = true

        assertEquals(NextThing.DoneForToday(rollover), nextThing())
    }

    @Test
    fun `a listing that never answers costs the button the chain timeout and no more`() = runTest {
        publish("AAAA22", "zed", hoursAgo = 1)
        val slow = object : SharedPuzzleRepository by shared {
            override suspend fun listPublic(filter: CommunityFilter): CommunityListResult {
                delay(10.seconds)
                return shared.listPublic(filter)
            }
        }

        assertEquals(NextThing.DoneForToday(rollover), nextThing(observe(slow)))
        assertEquals(2_000, currentTime)
    }

    @Test
    fun `a listing that throws offers nothing and is logged`() = runTest {
        val broken = object : SharedPuzzleRepository by shared {
            override suspend fun listPublic(filter: CommunityFilter): CommunityListResult = error("boom")
        }

        assertEquals(NextThing.DoneForToday(rollover), nextThing(observe(broken)))
        assertTrue(failures.single() is IllegalStateException)
    }

    @Test
    fun `a play again or a practice solve gets no next thing and lists nothing`() = runTest {
        publish("AAAA22", "zed", hoursAgo = 1)

        assertNull(nextThing(playAgain = true))
        assertNull(nextThing(practice = true))
    }

    private companion object {
        const val HOUR = 60L * 60 * 1000
    }
}
