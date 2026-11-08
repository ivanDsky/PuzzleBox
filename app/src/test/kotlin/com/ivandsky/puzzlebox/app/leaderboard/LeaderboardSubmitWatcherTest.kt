package com.ivandsky.puzzlebox.app.leaderboard

import android.app.Application
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.leaderboard.SubmitError
import com.ivandsky.puzzlebox.data.leaderboard.SubmitResult
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatus
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatusTracker
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    private val calendar = FakeGameCalendar(day)

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

        LeaderboardSubmitWatcher(records, identity, leaderboard, calendar, scope, SubmitStatusTracker()).start()
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
        LeaderboardSubmitWatcher(records, identity, leaderboard, calendar, scope, SubmitStatusTracker()).start()
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
        LeaderboardSubmitWatcher(records, identity, leaderboard, calendar, scope, SubmitStatusTracker()).start()
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

        LeaderboardSubmitWatcher(records, identity, leaderboard, calendar, scope, SubmitStatusTracker()).start()
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

        LeaderboardSubmitWatcher(records, identity, leaderboard, calendar, scope, SubmitStatusTracker()).start()
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
        LeaderboardSubmitWatcher(records, identity, leaderboard, calendar, scope, SubmitStatusTracker()).start()
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

        LeaderboardSubmitWatcher(records, identity, leaderboard, calendar, scope, SubmitStatusTracker()).start()
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

    /**
     * F2's app-start half: the dedup set is per-process, so a fresh watcher (new
     * process, same records store) retries a record the previous process deferred,
     * without waiting for any new records emission — the StateFlow's current value
     * is enough. The status tracker is shared to show the full Posting → Deferred →
     * Posting → Posted arc across the two instances.
     */
    @Test
    fun `a fresh watcher retries a deferred record (F2)`() = runTest {
        val records = FakeCompletionRepository(listOf(record()))
        val identity = FakeIdentityService(initial = Identity("u1", "Ann", null))
        var attempts = 0
        val leaderboard = FakeLeaderboardRepository(
            nextSubmitResult = { entry ->
                attempts++
                if (attempts == 1) SubmitResult.Err(SubmitError.Unavailable("offline")) else SubmitResult.Ok(entry)
            },
        )
        val scheduler = TestCoroutineScheduler()
        val statuses = SubmitStatusTracker()
        val seen = scheduler.statusesSeen(statuses)

        LeaderboardSubmitWatcher(records, identity, leaderboard, calendar, scope(scheduler), statuses).start()
        scheduler.advanceUntilIdle()

        assertEquals(1, attempts) // attempted once, failed: nothing posted
        assertEquals(listOf(SubmitStatus.Posting, SubmitStatus.Deferred), seen)

        // No setRecords here: a new watcher over the unchanged store still retries.
        LeaderboardSubmitWatcher(records, identity, leaderboard, calendar, scope(scheduler), statuses).start()
        scheduler.advanceUntilIdle()

        assertEquals(listOf(SubmitStatus.Posting, SubmitStatus.Deferred, SubmitStatus.Posting, SubmitStatus.Posted), seen)
        assertEquals(2, attempts) // the retry came from the fresh watcher
    }

    private fun zipRecord(d: LocalDate, n: Int) = record(ref = ZipPuzzleRef(PuzzleKey.Daily(d, n)))

    @Test
    fun `only the game's today and yesterday submit (A7)`() = runTest {
        val records = FakeCompletionRepository(
            listOf(zipRecord(day, 592), zipRecord(day.minusDays(1), 591), zipRecord(day.minusDays(2), 590)),
        )
        val identity = FakeIdentityService(initial = Identity("u1", "Ann", null))
        val leaderboard = FakeLeaderboardRepository()
        val scheduler = TestCoroutineScheduler()
        LeaderboardSubmitWatcher(records, identity, leaderboard, calendar, scope(scheduler), SubmitStatusTracker()).start()
        scheduler.advanceUntilIdle()

        assertEquals(
            listOf("zip/2026-10-31/592", "zip/2026-10-30/591"),
            leaderboard.submitted.map { it.puzzleRef },
        )
    }

    @Test
    fun `the window follows the calendar after a rollover`() = runTest {
        val records = FakeCompletionRepository(listOf(zipRecord(day.minusDays(1), 591)))
        val identity = FakeIdentityService(initial = Identity("u1", "Ann", null))
        val leaderboard = FakeLeaderboardRepository()
        val scheduler = TestCoroutineScheduler()
        LeaderboardSubmitWatcher(records, identity, leaderboard, calendar, scope(scheduler), SubmitStatusTracker()).start()
        scheduler.advanceUntilIdle()
        assertEquals(1, leaderboard.submitted.size)

        calendar.setAll(day.plusDays(1))
        records.setRecords(
            listOf(zipRecord(day.minusDays(1), 591), zipRecord(day, 592), zipRecord(day.plusDays(1), 593)),
        )
        scheduler.advanceUntilIdle()

        // 591 is now day-2 (and already marked); 592 yesterday and 593 today submit.
        assertEquals(
            listOf("zip/2026-10-30/591", "zip/2026-10-31/592", "zip/2026-11-01/593"),
            leaderboard.submitted.map { it.puzzleRef },
        )
    }

    @Test
    fun `a rejected submit is not retried on the next emission`() = runTest {
        val records = FakeCompletionRepository(listOf(zipRecord(day, 592)))
        val identity = FakeIdentityService(initial = Identity("u1", "Ann", null))
        val leaderboard = FakeLeaderboardRepository(
            nextSubmitResult = { SubmitResult.Err(SubmitError.Rejected("denied")) },
        )
        val scheduler = TestCoroutineScheduler()
        LeaderboardSubmitWatcher(records, identity, leaderboard, calendar, scope(scheduler), SubmitStatusTracker()).start()
        scheduler.advanceUntilIdle()
        records.setRecords(listOf(zipRecord(day, 592), zipRecord(day, 593)))
        scheduler.advanceUntilIdle()

        assertEquals(
            listOf("zip/2026-10-31/592", "zip/2026-10-31/593"),
            leaderboard.submitted.map { it.puzzleRef },
        )
    }

    /** Every status [statuses] reports for u1's post to today's zip board, from now on. */
    private fun TestCoroutineScheduler.statusesSeen(statuses: SubmitStatusTracker): List<SubmitStatus> {
        val seen = mutableListOf<SubmitStatus>()
        CoroutineScope(UnconfinedTestDispatcher(this) + Job()).launch {
            statuses.observe("u1", LeaderboardRef("zip", day)).collect { seen += it }
        }
        return seen
    }

    @Test
    fun `a successful submit goes Posting then Posted`() = runTest {
        val records = FakeCompletionRepository(listOf(record()))
        val identity = FakeIdentityService(initial = Identity("u1", "Ann", null))
        val scheduler = TestCoroutineScheduler()
        val statuses = SubmitStatusTracker()
        val seen = scheduler.statusesSeen(statuses)

        LeaderboardSubmitWatcher(records, identity, FakeLeaderboardRepository(), calendar, scope(scheduler), statuses).start()
        scheduler.advanceUntilIdle()

        assertEquals(listOf(SubmitStatus.Posting, SubmitStatus.Posted), seen)
    }

    @Test
    fun `a deferred submit posts on a later emission`() = runTest {
        val records = FakeCompletionRepository(listOf(record()))
        val identity = FakeIdentityService(initial = Identity("u1", "Ann", null))
        var attempts = 0
        val leaderboard = FakeLeaderboardRepository(
            nextSubmitResult = { entry ->
                attempts++
                if (attempts == 1) SubmitResult.Err(SubmitError.Unavailable("offline")) else SubmitResult.Ok(entry)
            },
        )
        val scheduler = TestCoroutineScheduler()
        val statuses = SubmitStatusTracker()
        val seen = scheduler.statusesSeen(statuses)

        LeaderboardSubmitWatcher(records, identity, leaderboard, calendar, scope(scheduler), statuses).start()
        scheduler.advanceUntilIdle()
        assertEquals(listOf(SubmitStatus.Posting, SubmitStatus.Deferred), seen)

        // Yesterday's record changes the list (an identical one would conflate) without sharing today's board.
        records.setRecords(listOf(record(), zipRecord(day.minusDays(1), 591)))
        scheduler.advanceUntilIdle()

        assertEquals(listOf(SubmitStatus.Posting, SubmitStatus.Deferred, SubmitStatus.Posting, SubmitStatus.Posted), seen)
    }

    @Test
    fun `a rejected submit stays Rejected and is not retried`() = runTest {
        val records = FakeCompletionRepository(listOf(record()))
        val identity = FakeIdentityService(initial = Identity("u1", "Ann", null))
        val leaderboard = FakeLeaderboardRepository(
            nextSubmitResult = { SubmitResult.Err(SubmitError.Rejected("denied")) },
        )
        val scheduler = TestCoroutineScheduler()
        val statuses = SubmitStatusTracker()
        val seen = scheduler.statusesSeen(statuses)

        LeaderboardSubmitWatcher(records, identity, leaderboard, calendar, scope(scheduler), statuses).start()
        scheduler.advanceUntilIdle()
        records.setRecords(listOf(record(), zipRecord(day.minusDays(1), 591)))
        scheduler.advanceUntilIdle()

        assertEquals(listOf(SubmitStatus.Posting, SubmitStatus.Rejected), seen)
        assertEquals(1, leaderboard.submitted.count { it.puzzleRef == "zip/2026-10-31/592" })
    }

    @Test
    fun `signed out writes no status`() = runTest {
        val records = FakeCompletionRepository(listOf(record()))
        val scheduler = TestCoroutineScheduler()
        val statuses = SubmitStatusTracker()
        val seen = scheduler.statusesSeen(statuses)
        val leaderboard = FakeLeaderboardRepository(
            nextSubmitResult = { SubmitResult.Err(SubmitError.Rejected("denied")) },
        )

        LeaderboardSubmitWatcher(records, FakeIdentityService(initial = null), leaderboard, calendar, scope(scheduler), statuses).start()
        scheduler.advanceUntilIdle()

        assertEquals(listOf(SubmitStatus.Posting), seen) // the unread default, never written
        assertTrue(leaderboard.submitted.isEmpty())
    }
}
