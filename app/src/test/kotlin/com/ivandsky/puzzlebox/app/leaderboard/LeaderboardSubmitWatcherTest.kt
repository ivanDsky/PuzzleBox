package com.ivandsky.puzzlebox.app.leaderboard

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.PuzzleBoxApplication
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.visibility.VisibilityRepository
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import org.koin.android.ext.koin.androidContext
import android.content.Context
import org.junit.Assert.assertFalse
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.instance.SingleInstanceFactory
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import org.koin.mp.KoinPlatform
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RuntimeEnvironment
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.leaderboard.SubmitError
import com.ivandsky.puzzlebox.data.leaderboard.SubmitResult
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatus
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatusTracker
import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSkippedPosts
import com.ivandsky.puzzlebox.testing.repository.FakeVisibilityRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
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
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
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

    private fun watchWith(own: OwnTier, leaderboard: FakeLeaderboardRepository, scheduler: TestCoroutineScheduler): FakeVisibilityRepository {
        val visibility = FakeVisibilityRepository(own)
        LeaderboardSubmitWatcher(
            FakeCompletionRepository(listOf(record())),
            FakeIdentityService(initial = Identity("u1", "Ann", null)),
            leaderboard,
            calendar,
            scope(scheduler),
            SubmitStatusTracker(),
            visibility,
        ).start()
        scheduler.advanceUntilIdle()
        return visibility
    }

    @Test
    fun `Hidden posts nothing`() = runTest {
        val leaderboard = FakeLeaderboardRepository()
        watchWith(OwnTier.Chosen(VisibilityTier.Hidden), leaderboard, TestCoroutineScheduler())
        assertTrue(leaderboard.submitted.isEmpty())
    }

    @Test
    fun `Unknown posts nothing until the tier loads, then posts for Board`() = runTest {
        val leaderboard = FakeLeaderboardRepository()
        val scheduler = TestCoroutineScheduler()
        val visibility = watchWith(OwnTier.Unknown, leaderboard, scheduler)
        assertTrue(leaderboard.submitted.isEmpty())

        visibility.emitOwn(OwnTier.Chosen(VisibilityTier.Board))
        scheduler.advanceUntilIdle()

        assertEquals(1, leaderboard.submitted.size)
    }

    @Test
    fun `Unknown then Hidden never posts`() = runTest {
        val leaderboard = FakeLeaderboardRepository()
        val scheduler = TestCoroutineScheduler()
        val visibility = watchWith(OwnTier.Unknown, leaderboard, scheduler)

        visibility.emitOwn(OwnTier.Chosen(VisibilityTier.Hidden))
        scheduler.advanceUntilIdle()

        assertTrue(leaderboard.submitted.isEmpty())
    }

    @Test
    fun `Unset and every posting tier post`() = runTest {
        for (own in listOf(OwnTier.Unset) + VisibilityTier.entries.filter { it.postsToBoards }.map { OwnTier.Chosen(it) }) {
            val leaderboard = FakeLeaderboardRepository()
            watchWith(own, leaderboard, TestCoroutineScheduler())
            assertEquals("$own", 1, leaderboard.submitted.size)
        }
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
    fun `play-agains and non-daily records never submit (A5)`() = runTest {
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

    /** One process of the watcher over shared stores: the fakes outlive it, so a second call is a relaunch. */
    private fun process(
        records: FakeCompletionRepository,
        visibility: FakeVisibilityRepository,
        leaderboard: FakeLeaderboardRepository,
        skipped: FakeSkippedPosts,
        scheduler: TestCoroutineScheduler,
    ) {
        LeaderboardSubmitWatcher(
            records,
            FakeIdentityService(initial = Identity("u1", "Ann", null)),
            leaderboard,
            calendar,
            scope(scheduler),
            SubmitStatusTracker(),
            visibility,
            skipped,
        ).start()
        scheduler.advanceUntilIdle()
    }

    @Test
    fun `a solve made while Hidden never posts after a switch to Board`() = runTest {
        val leaderboard = FakeLeaderboardRepository()
        val visibility = FakeVisibilityRepository(OwnTier.Chosen(VisibilityTier.Hidden))
        val records = FakeCompletionRepository(listOf(record()))
        val scheduler = TestCoroutineScheduler()
        process(records, visibility, leaderboard, FakeSkippedPosts(), scheduler)

        visibility.emitOwn(OwnTier.Chosen(VisibilityTier.Board))
        records.setRecords(listOf(record(), zipRecord(day, 593))) // the next emission re-reads every record
        scheduler.advanceUntilIdle()

        assertEquals(listOf("zip/2026-10-31/593"), leaderboard.submitted.map { it.puzzleRef })
    }

    @Test
    fun `a solve made while Hidden stays unposted across a relaunch on Board`() = runTest {
        val leaderboard = FakeLeaderboardRepository()
        val skipped = FakeSkippedPosts()
        val records = FakeCompletionRepository(listOf(record()))
        process(records, FakeVisibilityRepository(OwnTier.Chosen(VisibilityTier.Hidden)), leaderboard, skipped, TestCoroutineScheduler())

        process(records, FakeVisibilityRepository(OwnTier.Chosen(VisibilityTier.Board)), leaderboard, skipped, TestCoroutineScheduler())

        assertTrue(leaderboard.submitted.isEmpty())
    }

    @Test
    fun `Board to Hidden to Board posts the Board solves and never the Hidden one`() = runTest {
        val leaderboard = FakeLeaderboardRepository()
        val skipped = FakeSkippedPosts()
        val visibility = FakeVisibilityRepository(OwnTier.Chosen(VisibilityTier.Board))
        val records = FakeCompletionRepository(listOf(record()))
        val scheduler = TestCoroutineScheduler()
        process(records, visibility, leaderboard, skipped, scheduler)

        visibility.emitOwn(OwnTier.Chosen(VisibilityTier.Hidden))
        records.setRecords(listOf(record(), zipRecord(day.minusDays(1), 591)))
        scheduler.advanceUntilIdle()
        visibility.emitOwn(OwnTier.Chosen(VisibilityTier.Board))
        records.setRecords(listOf(record(), zipRecord(day.minusDays(1), 591), zipRecord(day, 593)))
        scheduler.advanceUntilIdle()

        assertEquals(setOf("zip/2026-10-31/592", "zip/2026-10-31/593"), leaderboard.submitted.map { it.puzzleRef }.toSet())
    }

    /** The watcher as the real graph builds it, over fakes, with the hub flag [on] or off and [own] stored. */
    private fun graphPostsWith(on: Boolean, own: OwnTier): Int {
        val leaderboard = FakeLeaderboardRepository()
        val scheduler = TestCoroutineScheduler()
        val koinApp = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(
                PuzzleBoxApplication.appModules + module {
                    single<ReleaseFlags> { if (on) FakeReleaseFlags(ReleaseFlag.LeaderboardHub) else FakeReleaseFlags() }
                    single<VisibilityRepository> { FakeVisibilityRepository(own) }
                    single<CompletionRepository> { FakeCompletionRepository(listOf(record())) }
                    single<IdentityService> { FakeIdentityService(initial = Identity("u1", "Ann", null)) }
                    single<LeaderboardRepository> { leaderboard }
                    single<GameCalendar> { calendar }
                    single<CoroutineScope> { scope(scheduler) }
                },
            )
        }
        try {
            koinApp.koin.get<LeaderboardSubmitWatcher>().start()
            scheduler.advanceUntilIdle()
        } finally {
            stopKoin()
        }
        return leaderboard.submitted.size
    }

    @Test
    fun `with the hub off a solve posts whatever tier is stored`() {
        for (own in listOf(OwnTier.Chosen(VisibilityTier.Hidden), OwnTier.Unknown, OwnTier.Unset)) {
            assertEquals("$own", 1, graphPostsWith(on = false, own = own))
        }
    }

    @Test
    fun `with the hub on a stored Hidden still posts nothing`() {
        assertEquals(0, graphPostsWith(on = true, own = OwnTier.Chosen(VisibilityTier.Hidden)))
    }

    /**
     * onCreate is the only place the publisher starts, so the real application runs it on a Firebase-initialised context,
     * with the hub flag [on] or off as the debug build's stored override. Returns whether the publisher was created.
     */
    @OptIn(KoinInternalApi::class)
    private fun publisherCreatedAfterStart(on: Boolean): Boolean {
        val context = RuntimeEnvironment.getApplication()
        initialiseFirebase(context)
        context.getSharedPreferences("release_flags", Context.MODE_PRIVATE).edit()
            .putBoolean(ReleaseFlag.LeaderboardHub.name, on).commit()
        val application = PuzzleBoxApplication()
        ReflectionHelpers.callInstanceMethod<Unit>(application, "attachBaseContext", ClassParameter.from(Context::class.java, context))
        application.onCreate()
        try {
            return KoinPlatform.getKoin().instanceRegistry.instances.values
                .filterIsInstance<SingleInstanceFactory<*>>()
                .first { it.beanDefinition.primaryType == DailyReplayPublisher::class }
                .isCreated()
        } finally {
            stopKoin()
        }
    }

    /** :app has no Firebase on its test classpath, and the graph's identity service needs a default app; reflection keeps the build file alone. */
    private fun initialiseFirebase(context: Context) {
        val app = Class.forName("com.google.firebase.FirebaseApp")
        val options = Class.forName("com.google.firebase.FirebaseOptions")
        if ((app.getMethod("getApps", Context::class.java).invoke(null, context) as List<*>).isNotEmpty()) return
        val builder = Class.forName("com.google.firebase.FirebaseOptions\$Builder").getConstructor().newInstance()
        builder.javaClass.getMethod("setApplicationId", String::class.java).invoke(builder, "1:1:android:1")
        builder.javaClass.getMethod("setApiKey", String::class.java).invoke(builder, "key")
        val built = builder.javaClass.getMethod("build").invoke(builder)
        app.getMethod("initializeApp", Context::class.java, options).invoke(null, context, built)
    }

    @Test
    fun `with the hub off the application never starts the publisher`() {
        assertFalse(publisherCreatedAfterStart(on = false))
    }

    @Test
    fun `with the hub on the application starts the publisher`() {
        assertTrue(publisherCreatedAfterStart(on = true))
    }
}
