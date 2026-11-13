package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatus
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatusTracker
import com.ivandsky.puzzlebox.data.model.PuzzleRefKeys
import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.SolveReplay
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyReplayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeVisibilityRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** A20: a record daily solve's replay is published once, when its post is accepted while the player is on the Replays tier. */
@OptIn(ExperimentalCoroutinesApi::class)
class SubmitDailyReplayTest {

    private val today = LocalDate.of(2026, 11, 13)
    private val puzzle = TestGames.Ref(TestGames.alpha, PuzzleKey.Daily(today, 7))
    private val board = LeaderboardRef("alpha", today)
    private val firstSolve = replay(1)
    private val secondSolve = replay(2)

    private val completions = FakeCompletionRepository()
    private val identity = FakeIdentityService(Identity(ME, "Ann", null))
    private val statuses = SubmitStatusTracker()
    private val visibility = FakeVisibilityRepository(OwnTier.Chosen(VisibilityTier.Replays))
    private val replays = FakeDailyReplayRepository(uid = ME)
    private val publish =
        SubmitDailyReplay(completions, identity, FakeGameCalendar(today), statuses, visibility, replays)

    @Test
    fun `an accepted record solve on the Replays tier publishes its replay`() = runTest {
        completions.onCompleted(solve(), firstSolve)
        start()

        accept()

        assertEquals(listOf(board to firstSolve), published())
    }

    @Test
    fun `nothing is published until the post is accepted`() = runTest {
        completions.onCompleted(solve(), firstSolve)
        start()

        status(SubmitStatus.Posting)
        status(SubmitStatus.Deferred)
        assertEquals(emptyList<Any>(), published())

        accept()
        assertEquals(listOf(board to firstSolve), published())
    }

    @Test
    fun `the replay is published once however often the solves and the post change`() = runTest {
        completions.onCompleted(solve(), firstSolve)
        start()
        accept()

        completions.onCompleted(solve(TestGames.Ref(TestGames.beta, PuzzleKey.Daily(today, 3))))
        status(SubmitStatus.Posting)
        accept()

        assertEquals(listOf(board to firstSolve), published())
    }

    @Test
    fun `a failed publish is not tried again`() = runTest {
        replays.failPublish = true
        completions.onCompleted(solve(), firstSolve)
        start()
        accept()

        completions.onCompleted(solve(TestGames.Ref(TestGames.beta, PuzzleKey.Daily(today, 3))))
        runCurrent()

        assertEquals(1, replays.publishCalls.size)
    }

    @Test
    fun `tiers below Replays never publish`() = runTest {
        start()
        val below = listOf(
            OwnTier.Unset,
            OwnTier.Chosen(VisibilityTier.Hidden),
            OwnTier.Chosen(VisibilityTier.Board),
            OwnTier.Chosen(VisibilityTier.Duel),
        )

        below.forEachIndexed { index, tier ->
            val ref = TestGames.Ref(GameId("game$index"), PuzzleKey.Daily(today, 7))
            visibility.emitOwn(tier)
            completions.onCompleted(solve(ref), firstSolve)
            accept(ref)
        }

        assertEquals(emptyList<Any>(), published())
    }

    @Test
    fun `an unknown tier publishes nothing until it loads as Replays`() = runTest {
        visibility.emitOwn(OwnTier.Unknown)
        completions.onCompleted(solve(), firstSolve)
        start()

        accept()
        assertEquals(emptyList<Any>(), published())

        visibility.emitOwn(OwnTier.Chosen(VisibilityTier.Replays))
        runCurrent()
        assertEquals(listOf(board to firstSolve), published())
    }

    @Test
    fun `an unknown tier that loads below Replays publishes nothing, even after opting in`() = runTest {
        visibility.emitOwn(OwnTier.Unknown)
        completions.onCompleted(solve(), firstSolve)
        start()
        accept()

        visibility.emitOwn(OwnTier.Unset)
        runCurrent()
        visibility.emitOwn(OwnTier.Chosen(VisibilityTier.Replays))
        runCurrent()

        assertEquals(emptyList<Any>(), published())
    }

    @Test
    fun `a solve accepted before opting in stays private and the next solve is published`() = runTest {
        visibility.emitOwn(OwnTier.Unset)
        completions.onCompleted(solve(), firstSolve)
        start()
        accept()

        visibility.setOwnTier(VisibilityTier.Replays)
        completions.onCompleted(solve(isRecord = false), secondSolve)
        runCurrent()
        assertEquals(emptyList<Any>(), published())

        val next = TestGames.Ref(TestGames.beta, PuzzleKey.Daily(today, 3))
        completions.onCompleted(solve(next), secondSolve)
        accept(next)
        assertEquals(listOf(LeaderboardRef("beta", today) to secondSolve), published())
    }

    @Test
    fun `a play-again publishes nothing and the record solve stays the published one`() = runTest {
        completions.onCompleted(solve(), firstSolve)
        completions.onCompleted(solve(isRecord = false), secondSolve)
        start()

        accept()

        assertEquals(listOf(board to firstSolve), published())
    }

    @Test
    fun `yesterday's solve is published and an older one is not`() = runTest {
        val yesterday = TestGames.Ref(TestGames.alpha, PuzzleKey.Daily(today.minusDays(1), 6))
        val older = TestGames.Ref(TestGames.alpha, PuzzleKey.Daily(today.minusDays(2), 5))
        completions.onCompleted(solve(yesterday), firstSolve)
        completions.onCompleted(solve(older), secondSolve)
        start()

        accept(yesterday)
        accept(older)

        assertEquals(listOf(LeaderboardRef("alpha", today.minusDays(1)) to firstSolve), published())
    }

    @Test
    fun `a solve without a stored replay publishes nothing`() = runTest {
        completions.onCompleted(solve(), replay = null)
        start()

        accept()

        assertEquals(0, replays.publishCalls.size)
    }

    @Test
    fun `a post accepted after the account changed is not published`() = runTest {
        completions.onCompleted(solve(), firstSolve)
        start()

        identity.currentIdentity = Identity("u2", "Bo", null)
        runCurrent()
        accept()

        assertEquals(emptyList<Any>(), published())
    }

    private fun TestScope.start() {
        backgroundScope.launch { publish() }
        runCurrent()
    }

    private fun TestScope.status(status: SubmitStatus, ref: PuzzleRef = puzzle) {
        statuses.set(ME, PuzzleRefKeys.encode(ref), status)
        runCurrent()
    }

    private fun TestScope.accept(ref: PuzzleRef = puzzle) = status(SubmitStatus.Posted, ref)

    private fun published(): List<Pair<LeaderboardRef, SolveReplay?>> =
        replays.publishCalls.map { (board, blob) -> board to SolveReplay.of(blob) }

    private fun solve(ref: PuzzleRef = puzzle, isRecord: Boolean = true) = CompletionRecord(
        puzzleRef = ref,
        time = 95_000,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
        firstCompletedAt = Instant.ofEpochMilli(1_700_000_000_000),
        isRecord = isRecord,
    )

    private companion object {
        const val ME = "u1"

        fun replay(tag: Byte) = checkNotNull(SolveReplay.of(byteArrayOf(tag, 2, 3)))
    }
}
