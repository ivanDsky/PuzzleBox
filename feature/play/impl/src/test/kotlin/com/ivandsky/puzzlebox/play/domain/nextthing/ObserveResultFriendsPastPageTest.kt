package com.ivandsky.puzzlebox.play.domain.nextthing

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetNextRollover
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.moments.Moment
import com.ivandsky.puzzlebox.domain.ranks.LoadFriendsPastPage
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendUids
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.play.domain.ObserveFriendPuzzles
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * #1779: Result's Friends board counts a friend who placed past the board's read page, as the hub's does, with
 * `FriendsPastGlobalPage` on. The page is 99 strangers and Ann, 30 s; Bea, 70 s, is past it. A 60 s solve passes Bea
 * only if she is on the board.
 */
class ObserveResultFriendsPastPageTest {

    private val me = Identity("me", "me", null)
    private val registry = TestGames.registry(listOf(alpha, beta))
    private val clock = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC)
    private val identity = FakeIdentityService(initial = me)
    private val matches = FakeMatchRepository().apply { signedInUid = me.uid }
    private val board = LeaderboardRef(alpha.value, TODAY)
    private val leaderboard = FakeLeaderboardRepository()
    private val calendar = FakeGameCalendar(TODAY)
    private val daily = FakeDailyRepository().apply { listOf(alpha, beta).forEach { setToday(it, TestGames.puzzle(it)) } }
    private val completions = FakeCompletionRepository()

    private fun entry(uid: String, timeMs: Long) = LeaderboardEntry(uid, uid, "${alpha.value}/$TODAY/100", timeMs, 0, 1L)

    private suspend fun befriend(uid: String, code: String) {
        matches.create(MatchDraft(checkNotNull(ShareCode.parse(code)), 24, Player(me.uid, "me", null), null, listOf(MatchLeg(LegRef.Daily(alpha, TODAY, 100), null)), true, null))
        matches.signedInUid = uid
        matches.claim(checkNotNull(ShareCode.parse(code)), Player(uid, uid, null))
        matches.signedInUid = me.uid
    }

    private suspend fun scene() {
        befriend("ann", "AAAAA2")
        befriend("bea", "AAAAA3")
        leaderboard.setEntries(board, (1..99).map { entry("s$it", it * 100L) } + entry("ann", 30_000))
        leaderboard.setCount(board, 400)
        leaderboard.setPastPage(board, listOf(entry("bea", 70_000)))
    }

    private fun observe(friendsPast: LoadFriendsPastPage?) = ObserveResultNextThing(
        registry = registry,
        identity = identity,
        history = ObserveGameHistory(registry, daily, completions),
        todaySet = ObserveTodaySet(registry, FakeSavedPlayRepository(), completions, calendar, daily, SyncTodayPuzzles(registry, daily, calendar)),
        friendPuzzles = ObserveFriendPuzzles(FakeReceivedPuzzleRepository(), FakeSavedPlayRepository(), completions, registry, FakeHiddenPuzzles()),
        friendUids = ObserveFriendUids(matches, identity),
        leaderboard = leaderboard,
        visibility = FakeVisibilityRepository(),
        computeStreak = ComputeStreak(GetPuzzleDay(registry, clock)),
        puzzleDay = GetPuzzleDay(registry, clock),
        nextRollover = GetNextRollover(registry, clock),
        communityPuzzles = ListCommunityPuzzles(FakeSharedPuzzleRepository(), FakeHiddenPuzzles(), identity) { 0L },
        friendsPast = friendsPast,
    )

    private suspend fun moments(use: ObserveResultNextThing): List<Moment> {
        var last = ResultNextThing()
        use(alpha, TODAY, BestScore(60_000, 0), playAgain = false, practice = false, newBest = false).collect { last = it }
        return last.moments
    }

    @Test
    fun `a solve passes the friend past the read page`() = runTest {
        scene()

        assertEquals(listOf(Moment.PassedFriend("bea", "bea")), moments(observe(LoadFriendsPastPage(leaderboard))).filterIsInstance<Moment.PassedFriend>())
    }

    @Test
    fun `flag off, the friend past the read page is not on the board, as before`() = runTest {
        scene()

        assertEquals(emptyList<Moment>(), moments(observe(friendsPast = null)).filterIsInstance<Moment.PassedFriend>())
        assertEquals(emptyList<Any>(), leaderboard.playersFetched)
    }
}
