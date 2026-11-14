package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatus
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatusTracker
import com.ivandsky.puzzlebox.data.model.PuzzleRefKeys
import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.data.visibility.ReplayOptInStamp
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
import com.ivandsky.puzzlebox.testing.repository.FakeReplayOptInStamp
import com.ivandsky.puzzlebox.testing.repository.FakeVisibilityRepository
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A20: a record daily solve's replay is published once, when its post is accepted while the player is on the Replays tier
 * and opted in before the solve was made.
 */
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
    private val stamps = FakeReplayOptInStamp()
    private var nowMs = BEFORE_SOLVE_MS
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
        override fun instant(): Instant = Instant.ofEpochMilli(nowMs)
    }
    private val warnings = mutableListOf<Throwable>()
    private val publish = publishWith(stamps)

    private fun publishWith(stamp: ReplayOptInStamp) = SubmitDailyReplay(
        completions = completions,
        identity = identity,
        calendar = FakeGameCalendar(today),
        statuses = statuses,
        visibility = visibility,
        optIns = stamp,
        replays = replays,
        clock = clock,
        failures = FailureLog { _, _, cause -> warnings += cause },
    )

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

    @Test
    fun `a solve made before the opt-in is never published, even when its post is accepted afterwards`() = runTest {
        visibility.emitOwn(OwnTier.Unset)
        completions.onCompleted(solve(), firstSolve)
        start()

        nowMs = SOLVE_MS + 1
        visibility.setOwnTier(VisibilityTier.Replays)
        runCurrent()
        accept()

        assertEquals(emptyList<Any>(), published())
    }

    @Test
    fun `after a restart a solve made before the opt-in is not published when its post is accepted again`() = runTest {
        stamps.stamps[ME] = SOLVE_MS + 1
        completions.onCompleted(solve(), firstSolve)
        val restarted = publishWith(stamps)
        backgroundScope.launch { restarted() }
        runCurrent()

        accept()

        assertEquals(emptyList<Any>(), published())
        assertEquals(SOLVE_MS + 1, stamps.stamps[ME])
    }

    @Test
    fun `a stamp from an earlier run is kept and publishes a solve made after it`() = runTest {
        stamps.stamps[ME] = SOLVE_MS - 1
        nowMs = SOLVE_MS + 5_000
        completions.onCompleted(solve(), firstSolve)
        start()

        accept()

        assertEquals(listOf(board to firstSolve), published())
        assertEquals(SOLVE_MS - 1, stamps.stamps[ME])
    }

    @Test
    fun `a solve made after the opt-in is published once`() = runTest {
        visibility.emitOwn(OwnTier.Unset)
        start()

        nowMs = SOLVE_MS - 1
        visibility.setOwnTier(VisibilityTier.Replays)
        runCurrent()
        completions.onCompleted(solve(), firstSolve)
        accept()
        status(SubmitStatus.Posting)
        accept()

        assertEquals(listOf(board to firstSolve), published())
        assertEquals(SOLVE_MS - 1, stamps.stamps[ME])
    }

    @Test
    fun `dropping below Replays clears the stamp and the next opt-in stamps again, so gap solves never publish`() = runTest {
        start()
        assertEquals(BEFORE_SOLVE_MS, stamps.stamps[ME])

        nowMs = SOLVE_MS - 2_000
        visibility.setOwnTier(VisibilityTier.Board)
        runCurrent()
        assertNull(stamps.stamps[ME])

        completions.onCompleted(solve(), firstSolve)
        nowMs = SOLVE_MS + 1_000
        visibility.setOwnTier(VisibilityTier.Replays)
        runCurrent()
        assertEquals(SOLVE_MS + 1_000, stamps.stamps[ME])
        accept()
        assertEquals(emptyList<Any>(), published())

        val next = TestGames.Ref(TestGames.beta, PuzzleKey.Daily(today, 3))
        completions.onCompleted(solve(next, atMs = SOLVE_MS + 2_000), secondSolve)
        accept(next)
        assertEquals(listOf(LeaderboardRef("beta", today) to secondSolve), published())
    }

    @Test
    fun `an unknown tier neither stamps nor clears`() = runTest {
        start()

        visibility.emitOwn(OwnTier.Unknown)
        runCurrent()

        assertEquals(BEFORE_SOLVE_MS, stamps.stamps[ME])
    }

    @Test
    fun `a stamp that was not kept publishes nothing`() = runTest {
        val lost = object : ReplayOptInStamp {
            override suspend fun since(uid: String): Long? = null
            override suspend fun markIfAbsent(uid: String, atMs: Long) = Unit
            override suspend fun clear(uid: String) = Unit
        }
        completions.onCompleted(solve(), firstSolve)
        val unstamped = publishWith(lost)
        backgroundScope.launch { unstamped() }
        runCurrent()

        accept()

        assertEquals(emptyList<Any>(), published())
    }

    @Test
    fun `a solve without a completion time publishes nothing`() = runTest {
        completions.onCompleted(solve(atMs = null), firstSolve)
        start()

        accept()

        assertEquals(emptyList<Any>(), published())
    }

    @Test
    fun `stamps are kept per player`() = runTest {
        start()

        identity.currentIdentity = Identity("u2", "Bo", null)
        visibility.emitOwn(OwnTier.Chosen(VisibilityTier.Board))
        runCurrent()

        assertEquals(BEFORE_SOLVE_MS, stamps.stamps[ME])
        assertNull(stamps.stamps["u2"])
    }

    @Test
    fun `a stamp that cannot be written does not end the publisher`() = runTest {
        val running = backgroundScope.launch { publishWith(UnwritableStamp(stamps))() }
        runCurrent()

        assertTrue(running.isActive)
    }

    @Test
    fun `a stamp that cannot be cleared does not end the publisher`() = runTest {
        visibility.emitOwn(OwnTier.Chosen(VisibilityTier.Board))
        val running = backgroundScope.launch { publishWith(UnwritableStamp(stamps))() }
        runCurrent()

        assertTrue(running.isActive)
    }

    @Test
    fun `nothing is published while the stamp is unwritten, even for a solve made after an earlier stamp`() = runTest {
        stamps.stamps[ME] = SOLVE_MS - 1
        completions.onCompleted(solve(), firstSolve)
        backgroundScope.launch { publishWith(UnwritableStamp(stamps))() }
        runCurrent()

        accept()

        assertEquals(emptyList<Any>(), published())
    }

    @Test
    fun `publishing resumes once a stamp write succeeds`() = runTest {
        visibility.emitOwn(OwnTier.Unset)
        val store = UnwritableStamp(stamps)
        backgroundScope.launch { publishWith(store)() }
        runCurrent()

        store.failing = false
        visibility.setOwnTier(VisibilityTier.Replays)
        completions.onCompleted(solve(), firstSolve)
        accept()

        assertEquals(listOf(board to firstSolve), published())
    }

    @Test
    fun `a failed stamp write is reported once`() = runTest {
        val store = UnwritableStamp(stamps)
        backgroundScope.launch { publishWith(store)() }
        runCurrent()

        assertEquals(listOf<Throwable>(store.failure), warnings)
    }

    @Test
    fun `cancelling in the middle of a stamp write ends the publisher`() = runTest {
        assertTrue(cancelMidWrite().isCompleted)
    }

    @Test
    fun `a cancelled stamp write is not reported as a failed one`() = runTest {
        cancelMidWrite()

        assertEquals(emptyList<Throwable>(), warnings)
    }

    private fun TestScope.cancelMidWrite(): Job {
        val stuck = StuckStamp(stamps)
        val running = backgroundScope.launch { publishWith(stuck)() }
        runCurrent()
        assertTrue(stuck.writing)

        running.cancel()
        runCurrent()
        return running
    }

    @Test
    fun `after a failed clear a solve made below Replays stays private when its post is accepted after the next opt-in`() = runTest {
        optOutWithoutClearing()
        completions.onCompleted(solve(), firstSolve)

        nowMs = SOLVE_MS + 1_000
        visibility.setOwnTier(VisibilityTier.Replays)
        runCurrent()
        accept()

        assertEquals(emptyList<Any>(), published())
    }

    @Test
    fun `after a failed clear a solve whose post was accepted below Replays stays private after the next opt-in`() = runTest {
        optOutWithoutClearing()
        completions.onCompleted(solve(), firstSolve)
        accept()

        nowMs = SOLVE_MS + 1_000
        visibility.setOwnTier(VisibilityTier.Replays)
        runCurrent()

        assertEquals(emptyList<Any>(), published())
    }

    @Test
    fun `the opt-in after a failed clear is stamped afresh`() = runTest {
        optOutWithoutClearing()

        nowMs = SOLVE_MS + 1_000
        visibility.setOwnTier(VisibilityTier.Replays)
        runCurrent()

        assertEquals(SOLVE_MS + 1_000, stamps.stamps[ME])
    }

    /** Opts in, then drops below Replays on a store that cannot clear, which leaves the first stamp behind. */
    private fun TestScope.optOutWithoutClearing() {
        val store = UnwritableStamp(stamps, failing = false)
        backgroundScope.launch { publishWith(store)() }
        runCurrent()
        store.failing = true
        visibility.emitOwn(OwnTier.Chosen(VisibilityTier.Board))
        runCurrent()
        assertEquals(BEFORE_SOLVE_MS, stamps.stamps[ME])
        store.failing = false
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

    private fun solve(ref: PuzzleRef = puzzle, isRecord: Boolean = true, atMs: Long? = SOLVE_MS) = CompletionRecord(
        puzzleRef = ref,
        time = 95_000,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
        firstCompletedAt = atMs?.let(Instant::ofEpochMilli),
        isRecord = isRecord,
    )

    private companion object {
        const val ME = "u1"
        const val SOLVE_MS = 1_700_000_000_000
        const val BEFORE_SOLVE_MS = SOLVE_MS - 1_000_000

        fun replay(tag: Byte) = checkNotNull(SolveReplay.of(byteArrayOf(tag, 2, 3)))
    }
}

/** Reads go to [kept]; writes throw [failure] while [failing], like a disk that has stopped taking them. */
private class UnwritableStamp(
    private val kept: ReplayOptInStamp,
    var failing: Boolean = true,
) : ReplayOptInStamp by kept {
    val failure = IOException("stamp store is unwritable")

    override suspend fun markIfAbsent(uid: String, atMs: Long) {
        if (failing) throw failure
        kept.markIfAbsent(uid, atMs)
    }

    override suspend fun clear(uid: String) {
        if (failing) throw failure
        kept.clear(uid)
    }
}

/** Reads go to [kept]; an opt-in write never returns, like a disk write still under way. */
private class StuckStamp(private val kept: ReplayOptInStamp) : ReplayOptInStamp by kept {
    var writing = false

    override suspend fun markIfAbsent(uid: String, atMs: Long) {
        writing = true
        awaitCancellation()
    }
}
