package com.ivandsky.puzzlebox.app.leaderboard

import android.app.Application
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.leaderboard.SubmitError
import com.ivandsky.puzzlebox.data.leaderboard.SubmitResult
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The submit hook (brief §3): record writes are the only source of entries (A5),
 * submission is deduped per (uid, puzzleRef), and signed-out/offline failures never
 * surface (A7). Robolectric: the failure path logs through android.util.Log.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LeaderboardSubmitWatcherTest {

    private val day = LocalDate.of(2026, 10, 31)

    private fun record(
        ref: PuzzleRef = ZipPuzzleRef(PuzzleKey.Daily(day, 592)),
        timeMs: Long = 95_000,
        hints: Int = 2,
        isRecord: Boolean = true,
    ) = CompletionRecord(
        puzzleRef = ref,
        time = timeMs,
        hints = hints,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
        firstCompletedAt = Instant.ofEpochMilli(1_700_000_000_000),
        isRecord = isRecord,
    )

    private fun scope(scheduler: TestCoroutineScheduler) =
        CoroutineScope(StandardTestDispatcher(scheduler) + Job())

    @Test
    fun `new records submit exactly once`() = runTest {
        val records = FakeCompletionRepository(listOf(record()))
        val identity = FakeIdentityService(initial = Identity("u1", "Ann", null))
        val leaderboard = FakeLeaderboardRepository()
        val scheduler = TestCoroutineScheduler()
        val scope = scope(scheduler)

        LeaderboardSubmitWatcher(records, identity, leaderboard, scope).start()
        scheduler.advanceUntilIdle()

        assertEquals(1, leaderboard.submitted.size)
        assertEquals("zip/2026-10-31/592", leaderboard.submitted.single().puzzleRef)
        assertEquals("u1", leaderboard.submitted.single().uid)
        assertEquals("Ann", leaderboard.submitted.single().displayName)
    }

    @Test
    fun `records arriving before sign-in submit once identity lands`() = runTest {
        val records = FakeCompletionRepository(listOf(record()))
        val identity = FakeIdentityService(initial = null)
        val leaderboard = FakeLeaderboardRepository()
        val scheduler = TestCoroutineScheduler()
        val scope = scope(scheduler)
        LeaderboardSubmitWatcher(records, identity, leaderboard, scope).start()
        scheduler.advanceUntilIdle()
        assertTrue(leaderboard.submitted.isEmpty()) // A7: signed out = local-only

        identity.currentIdentity = Identity("u1", "Ann", null)
        scheduler.advanceUntilIdle()

        assertEquals(1, leaderboard.submitted.size)
    }

    @Test
    fun `re-emitted records dedup per (uid, puzzleRef)`() = runTest {
        val records = FakeCompletionRepository(listOf(record()))
        val identity = FakeIdentityService(initial = Identity("u1", "Ann", null))
        val leaderboard = FakeLeaderboardRepository()
        val scheduler = TestCoroutineScheduler()
        val scope = scope(scheduler)
        LeaderboardSubmitWatcher(records, identity, leaderboard, scope).start()
        scheduler.advanceUntilIdle()

        // Room re-emits the whole records list on any write.
        records.setRecords(listOf(record(), record(ref = ZipPuzzleRef(PuzzleKey.Daily(day, 593)))))
        scheduler.advanceUntilIdle()

        assertEquals(2, leaderboard.submitted.size)
        assertEquals(
            listOf("zip/2026-10-31/592", "zip/2026-10-31/593"),
            leaderboard.submitted.map { it.puzzleRef },
        )
    }

    @Test
    fun `replays and non-daily records never submit (A5)`() = runTest {
        val records = FakeCompletionRepository(
            listOf(record(isRecord = false), record(ref = ZipPuzzleRef(PuzzleKey.Daily(day, puzzleNumber = null)))),
        )
        val identity = FakeIdentityService(initial = Identity("u1", "Ann", null))
        val leaderboard = FakeLeaderboardRepository()
        val scheduler = TestCoroutineScheduler()
        val scope = scope(scheduler)

        LeaderboardSubmitWatcher(records, identity, leaderboard, scope).start()
        scheduler.advanceUntilIdle()

        assertTrue(leaderboard.submitted.isEmpty())
    }

    @Test
    fun `submit failures never propagate (A7)`() = runTest {
        val records = FakeCompletionRepository(listOf(record()))
        val identity = FakeIdentityService(initial = Identity("u1", "Ann", null))
        val leaderboard = FakeLeaderboardRepository(
            nextSubmitResult = { SubmitResult.Err(SubmitError.Unavailable("offline")) },
        )
        val scheduler = TestCoroutineScheduler()
        val scope = scope(scheduler)

        LeaderboardSubmitWatcher(records, identity, leaderboard, scope).start()
        scheduler.advanceUntilIdle() // must not throw

        assertEquals(1, leaderboard.submitted.size)
    }

    @Test
    fun `different identities submit separately`() = runTest {
        val records = FakeCompletionRepository(listOf(record()))
        val identity = FakeIdentityService(initial = Identity("u1", "Ann", null))
        val leaderboard = FakeLeaderboardRepository()
        val scheduler = TestCoroutineScheduler()
        val scope = scope(scheduler)
        LeaderboardSubmitWatcher(records, identity, leaderboard, scope).start()
        scheduler.advanceUntilIdle()
        assertEquals(1, leaderboard.submitted.size)

        identity.currentIdentity = Identity("u2", "Bo", null)
        scheduler.advanceUntilIdle()

        assertEquals(2, leaderboard.submitted.size)
        assertEquals("u2", leaderboard.submitted.last().uid)
    }

    @Test
    fun `unavailable submissions un-mark and retry on the next emission (M5)`() = runTest {
        val records = FakeCompletionRepository(listOf(record()))
        val identity = FakeIdentityService(initial = Identity("u1", "Ann", null))
        var attempts = 0
        val leaderboard = FakeLeaderboardRepository(
            nextSubmitResult = { entry ->
                attempts++
                if (attempts == 1) SubmitResult.Err(SubmitError.Unavailable("offline"))
                else SubmitResult.Ok(entry)
            },
        )
        val scheduler = TestCoroutineScheduler()
        val scope = scope(scheduler)

        LeaderboardSubmitWatcher(records, identity, leaderboard, scope).start()
        scheduler.advanceUntilIdle()
        assertEquals(1, attempts) // attempted once, failed

        // A records change re-emits the list (Room semantics) and must retry the
        // failed key — pre-fix it stayed marked. The identical single-record list
        // would conflate away on the StateFlow, so the write adds another day.
        records.setRecords(listOf(record(), record(ref = ZipPuzzleRef(PuzzleKey.Daily(day, 593)))))
        scheduler.advanceUntilIdle()

        assertEquals(3, attempts) // 592 retried, 593 first attempt
        assertEquals(
            listOf("zip/2026-10-31/592", "zip/2026-10-31/593"),
            leaderboard.submitted.drop(1).map { it.puzzleRef },
        )
    }
}
