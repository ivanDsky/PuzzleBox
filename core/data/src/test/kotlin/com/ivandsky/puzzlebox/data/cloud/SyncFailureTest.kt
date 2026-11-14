package com.ivandsky.puzzlebox.data.cloud

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSpace
import com.ivandsky.puzzlebox.testing.cloud.FakeSyncStateStore
import com.ivandsky.puzzlebox.testing.network.FakeNetworkStatus
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The engine's failure policy (ticket 13) under scripted failures on a virtual clock. */
class SyncFailureTest {

    private class MutableClock(var ms: Long) : Clock() {
        override fun instant(): Instant = Instant.ofEpochMilli(ms)
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }

    /** Logs the calls that cost the cloud something (`changedSince` and `put`), then delegates. */
    private class LoggingSpace(private val delegate: CloudSpace, private val log: MutableList<String>) : CloudSpace by delegate {
        override suspend fun changedSince(uid: String, kind: String, cursorMs: Long?): CloudResult<List<CloudDoc>> {
            log += "changedSince $kind $cursorMs"
            return delegate.changedSince(uid, kind, cursorMs)
        }

        override suspend fun put(uid: String, kind: String, docs: List<CloudDoc>): CloudResult<Unit> {
            log += "put $kind ${docs.size}"
            return delegate.put(uid, kind, docs)
        }
    }

    private val ann = Identity(uid = ANN, displayName = "Ann", photoUrl = null)
    private val identity = FakeIdentityService(ann)
    private val space = FakeCloudSpace(identity) { 1_000L }
    private val store = FakeSyncStateStore()
    private val clock = MutableClock(10_000L)
    private val network = FakeNetworkStatus(initial = false)
    private val log = mutableListOf<String>()
    private val warnings = mutableListOf<String>()
    private val loggingSpace = LoggingSpace(space, log)

    private fun TestScope.engine(
        kinds: List<SyncedKind>,
        scope: CoroutineScope = backgroundScope,
        store: SyncStateStore = this@SyncFailureTest.store,
    ): SyncedKindsCloudSync {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val main = dispatcher
            override val io = dispatcher
            override val default = dispatcher
        }
        return SyncedKindsCloudSync(
            kinds, loggingSpace, store, identity, CloudErase(loggingSpace, kinds, store, identity), clock, dispatchers, network, scope,
            warnings::add,
        )
    }

    private fun kind(name: String, rows: Int = 0) =
        RecordingKind(name).also { kind -> repeat(rows) { kind.edit("$name$it", mapOf("n" to it.toLong())) } }

    /** Moves virtual time by [ms] and runs everything due at the new time. */
    private fun TestScope.advance(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    private fun failures(count: Int, result: CloudResult<Nothing>) = repeat(count) { space.failNext(result) }

    private val pulls get() = log.count { it.startsWith("changedSince") }

    // initial state

    @Test
    fun `a signed-in start shows a first restore as soon as the engine exists`() = runTest {
        val engine = engine(listOf(kind("completions")))
        runCurrent()

        assertEquals(CloudSyncState.Syncing(firstRestore = true), engine.state.value)
        assertTrue(log.isEmpty())
    }

    @Test
    fun `a signed-in start after the restore was shown is not a first restore`() = runTest {
        store.markRestoreShown(ANN)
        val engine = engine(listOf(kind("completions")))
        runCurrent()

        assertEquals(CloudSyncState.Syncing(firstRestore = false), engine.state.value)
    }

    @Test
    fun `a signed-out start shows SignedOut and signing in turns it to Syncing`() = runTest {
        identity.currentIdentity = null
        val engine = engine(listOf(kind("completions")))
        runCurrent()
        assertEquals(CloudSyncState.SignedOut, engine.state.value)

        identity.currentIdentity = ann
        runCurrent()

        assertEquals(CloudSyncState.Syncing(firstRestore = true), engine.state.value)
    }

    @Test
    fun `a first run that goes offline shows Offline with no last time`() = runTest {
        space.failNext(CloudResult.Offline)
        val engine = engine(listOf(kind("completions")))
        runCurrent()

        engine.syncNow()

        assertEquals(CloudSyncState.Offline(null), engine.state.value)
    }

    @Test
    fun `a first run that is denied or exhausted shows Paused with no last time`() = runTest {
        space.failNext(CloudResult.Denied)
        val engine = engine(listOf(kind("completions")))
        runCurrent()
        engine.syncNow()
        assertEquals(CloudSyncState.Paused(PauseReason.Denied, null), engine.state.value)

        space.failNext(CloudResult.Exhausted)
        engine.syncNow()
        assertEquals(CloudSyncState.Paused(PauseReason.Exhausted, null), engine.state.value)
    }

    // state after a failure

    @Test
    fun `a failure after a success keeps the last success time`() = runTest {
        val engine = engine(listOf(kind("completions")))
        engine.syncNow()
        assertEquals(CloudSyncState.Synced(10_000L), engine.state.value)
        clock.ms = 50_000L

        space.failNext(CloudResult.Offline)
        engine.syncNow()
        assertEquals(CloudSyncState.Offline(10_000L), engine.state.value)

        space.failNext(CloudResult.Denied)
        engine.syncNow()
        assertEquals(CloudSyncState.Paused(PauseReason.Denied, 10_000L), engine.state.value)
    }

    // offline waits for a trigger

    @Test
    fun `offline with the network down schedules nothing and a later trigger syncs`() = runTest {
        network.setOnline(false)
        space.failNext(CloudResult.Offline)
        val engine = engine(listOf(kind("completions")))
        engine.syncNow()
        assertEquals(1, pulls)

        advance(24 * HOUR)

        assertEquals(1, pulls)
        assertEquals(CloudSyncState.Offline(null), engine.state.value)
        engine.syncNow()
        assertEquals(2, pulls)
        assertEquals(CloudSyncState.Synced(10_000L), engine.state.value)
    }

    @Test
    fun `a timeout while the network is up retries on 1, 2, 4 and 8 minutes and shows Offline meanwhile`() = runTest {
        network.setOnline(true)
        failures(4, CloudResult.Offline)
        val engine = engine(listOf(kind("completions")))
        engine.syncNow()
        assertEquals(1, pulls)

        var expected = 1
        for (minutes in listOf(1L, 2L, 4L)) {
            advance(minutes * MINUTE - 1)
            assertEquals("just before $minutes min", expected, pulls)
            advance(1)
            expected++
            assertEquals("at $minutes min", expected, pulls)
            assertEquals(CloudSyncState.Offline(null), engine.state.value)
        }

        advance(8 * MINUTE)

        assertEquals(5, pulls)
        assertEquals(CloudSyncState.Synced(10_000L), engine.state.value)
    }

    // backoff

    @Test
    fun `denied retries after 1, 2, 4, 8, 16, 30 and 30 minutes with no other calls`() = runTest {
        failures(8, CloudResult.Denied)
        val engine = engine(listOf(kind("completions")))
        engine.syncNow()
        assertEquals(1, pulls)

        var expected = 1
        for (minutes in listOf(1L, 2L, 4L, 8L, 16L, 30L, 30L)) {
            advance(minutes * MINUTE - 1)
            assertEquals("just before $minutes min", expected, pulls)
            advance(1)
            expected++
            assertEquals("at $minutes min", expected, pulls)
            assertEquals(CloudSyncState.Paused(PauseReason.Denied, null), engine.state.value)
        }
        assertTrue(log.all { it.startsWith("changedSince") })

        advance(30 * MINUTE)

        assertEquals(9, pulls)
        assertEquals(CloudSyncState.Synced(10_000L), engine.state.value)
    }

    @Test
    fun `exhausted retries on the same schedule and a success ends the pauses`() = runTest {
        failures(3, CloudResult.Exhausted)
        val engine = engine(listOf(kind("completions")))
        engine.syncNow()
        assertEquals(CloudSyncState.Paused(PauseReason.Exhausted, null), engine.state.value)

        advance(MINUTE)
        assertEquals(2, pulls)
        assertEquals(CloudSyncState.Paused(PauseReason.Exhausted, null), engine.state.value)
        advance(2 * MINUTE)
        assertEquals(3, pulls)
        advance(4 * MINUTE)
        assertEquals(4, pulls)
        assertEquals(CloudSyncState.Synced(10_000L), engine.state.value)

        advance(24 * HOUR)
        assertEquals(4, pulls)
    }

    @Test
    fun `a trigger tries sooner and its failure doubles the next delay`() = runTest {
        failures(3, CloudResult.Denied)
        val engine = engine(listOf(kind("completions")))
        engine.syncNow()
        advance(30_000)

        engine.syncNow()

        assertEquals(2, pulls)
        advance(MINUTE) // the first timer would fire here; the trigger replaced it
        assertEquals(2, pulls)
        advance(MINUTE) // 2 min after the trigger's failure
        assertEquals(3, pulls)
        advance(4 * MINUTE - 1)
        assertEquals(3, pulls)
        advance(1)
        assertEquals(4, pulls)
        assertEquals(CloudSyncState.Synced(10_000L), engine.state.value)
    }

    @Test
    fun `after a success the next failure starts again at one minute`() = runTest {
        failures(2, CloudResult.Denied)
        val engine = engine(listOf(kind("completions")))
        engine.syncNow()
        advance(MINUTE)
        advance(2 * MINUTE)
        assertEquals(CloudSyncState.Synced(10_000L), engine.state.value)

        space.failNext(CloudResult.Denied)
        engine.syncNow()
        val before = pulls
        advance(MINUTE)

        assertEquals(before + 1, pulls)
        assertEquals(CloudSyncState.Synced(10_000L), engine.state.value)
    }

    @Test
    fun `signing out cancels the pending retry`() = runTest {
        failures(1, CloudResult.Denied)
        val engine = engine(listOf(kind("completions")))
        engine.syncNow()
        identity.currentIdentity = null
        runCurrent()

        advance(24 * HOUR)

        assertEquals(1, pulls)
        assertEquals(CloudSyncState.SignedOut, engine.state.value)
    }

    @Test
    fun `a wrong account ends the run without a retry`() = runTest {
        space.failNext(CloudResult.WrongAccount)
        val engine = engine(listOf(kind("completions")))
        engine.syncNow()

        advance(24 * HOUR)

        assertEquals(1, pulls)
    }

    // a thrown run

    /** What escaped to the scope's handler: the app scope has none, so anything here would crash the process. */
    private val escaped = mutableListOf<Throwable>()

    /** The scope's job; a test cancels it last, or runTest would run a pause's retries on virtual time without end. */
    private val appJob = SupervisorJob()

    private fun TestScope.appScope() = CoroutineScope(
        StandardTestDispatcher(testScheduler) + appJob + CoroutineExceptionHandler { _, e -> escaped += e },
    )

    @Test
    fun `a kind that throws in pending pauses the run, spares the other kinds and retries`() = runTest {
        val completions = kind("completions", rows = 1)
        val drafts = kind("drafts", rows = 1)
        completions.onCall = { if (it == "pending") error("disk full") }
        val engine = engine(listOf(completions, drafts), appScope())

        engine.syncNow()

        assertEquals(CloudSyncState.Paused(PauseReason.Exhausted, null), engine.state.value)
        assertEquals(listOf("drafts0"), space.docs(ANN, "drafts").map { it.id })
        assertEquals(listOf("completions0"), completions.pendingRowIds)
        assertTrue(escaped.isEmpty())
        assertEquals(listOf("sync run failed: IllegalStateException"), warnings)
        completions.onCall = {}

        advance(MINUTE)

        assertEquals(listOf("completions0"), space.docs(ANN, "completions").map { it.id })
        assertEquals(CloudSyncState.Synced(10_000L), engine.state.value)
        assertTrue(escaped.isEmpty())
        appJob.cancel()
    }

    @Test
    fun `a kind that throws in apply keeps its cursor, spares the other kinds and retries`() = runTest {
        space.put(ANN, "completions", listOf(CloudDoc("g9", mapOf("n" to 1L))))
        val completions = kind("completions")
        val drafts = kind("drafts", rows = 1)
        completions.onCall = { if (it == "apply") error("disk full") }
        val engine = engine(listOf(completions, drafts), appScope())

        engine.syncNow()

        assertEquals(CloudSyncState.Paused(PauseReason.Exhausted, null), engine.state.value)
        assertNull(store.cursor(ANN, "completions"))
        assertEquals(listOf("drafts0"), space.docs(ANN, "drafts").map { it.id })
        completions.onCall = {}

        advance(MINUTE)

        assertEquals(listOf("g9"), completions.rows)
        assertEquals(CloudSyncState.Synced(10_000L), engine.state.value)
        assertTrue(escaped.isEmpty())
        appJob.cancel()
    }

    @Test
    fun `a retry whose run throws does not escape the scope and keeps backing off`() = runTest {
        space.failNext(CloudResult.Denied)
        val completions = kind("completions", rows = 1)
        completions.onCall = { if (it == "pending") error("disk full") }
        val engine = engine(listOf(completions), appScope())
        engine.syncNow()
        assertEquals(CloudSyncState.Paused(PauseReason.Denied, null), engine.state.value)

        advance(MINUTE)

        assertTrue(escaped.isEmpty())
        assertEquals(CloudSyncState.Paused(PauseReason.Exhausted, null), engine.state.value)
        completions.onCall = {}
        advance(2 * MINUTE)
        assertEquals(CloudSyncState.Synced(10_000L), engine.state.value)
        assertTrue(escaped.isEmpty())
        appJob.cancel()
    }

    @Test
    fun `a store read that throws at start or in a run does not escape the scope`() = runTest {
        val failing = object : SyncStateStore by store {
            override suspend fun restoreShown(uid: String): Boolean = error("datastore")
        }
        val engine = engine(listOf(kind("completions")), appScope(), failing)
        runCurrent()

        engine.syncNow()

        assertTrue(escaped.isEmpty())
        assertFalse(engine.state.value is CloudSyncState.Syncing)
        appJob.cancel()
    }

    // no failure changes persisted state

    @Test
    fun `a denied or exhausted put leaves the row, the marker and the cloud as they were`() = runTest {
        for (failure in listOf(CloudResult.Denied, CloudResult.Exhausted)) {
            val completions = kind("completions", rows = 2)
            completions.onCall = { if (it == "pending") space.failNext(failure) }
            val engine = engine(listOf(completions))

            engine.syncNow()

            assertTrue(completions.calls("acknowledge").isEmpty())
            assertEquals(listOf("completions0", "completions1"), completions.pendingRowIds)
            assertEquals(mapOf("n" to 1L), completions.row("completions1"))
            assertTrue(space.docs(ANN, "completions").isEmpty())
            assertEquals(0, space.writes)
        }
    }

    @Test
    fun `a failed pull leaves the cursor, the restore flag and the rows alone`() = runTest {
        val completions = kind("completions", rows = 1)
        for (failure in listOf(CloudResult.Offline, CloudResult.Denied, CloudResult.Exhausted)) {
            space.failNext(failure)
            val engine = engine(listOf(completions))

            engine.syncNow()

            assertNull(store.cursor(ANN, "completions"))
            assertFalse(store.restoreShown(ANN))
            assertTrue(completions.calls("apply").isEmpty())
            assertEquals(listOf("completions0"), completions.pendingRowIds)
        }
    }

    @Test
    fun `a failing retry changes nothing either and the row goes up when the cloud answers`() = runTest {
        val completions = kind("completions", rows = 1)
        failures(2, CloudResult.Denied)
        completions.onCall = { if (it == "pending") space.failNext(CloudResult.Exhausted) }
        val engine = engine(listOf(completions))
        engine.syncNow() // pull denied
        advance(MINUTE) // pull denied
        assertEquals(listOf("completions0"), completions.pendingRowIds)
        advance(2 * MINUTE) // pull ok, put exhausted
        assertEquals(CloudSyncState.Paused(PauseReason.Exhausted, null), engine.state.value)
        assertEquals(listOf("completions0"), completions.pendingRowIds)
        assertEquals(0, space.writes)
        completions.onCall = {}

        advance(4 * MINUTE)

        assertTrue(completions.pendingRowIds.isEmpty())
        assertEquals(listOf("completions0"), space.docs(ANN, "completions").map { it.id })
        assertEquals(CloudSyncState.Synced(10_000L), engine.state.value)
    }

    @Test
    fun `a hung write leaves the marker alone and the next process sends the row again`() = runTest {
        val completions = kind("completions", rows = 1)
        completions.onCall = { if (it == "pending") space.hangNext() }
        val dying = Job()
        val first = engine(listOf(completions), CoroutineScope(StandardTestDispatcher(testScheduler) + dying))
        val caller = launch { runCatching { first.syncNow() } }
        runCurrent()

        assertTrue(log.contains("put completions 1"))
        assertEquals(listOf("completions0"), completions.pendingRowIds)
        dying.cancel()
        caller.join()
        completions.onCall = {}

        val second = engine(listOf(completions))
        second.syncNow()

        assertEquals(listOf("completions0"), space.docs(ANN, "completions").map { it.id })
        assertTrue(completions.pendingRowIds.isEmpty())
        assertEquals(CloudSyncState.Synced(10_000L), second.state.value)
    }

    // write cap

    @Test
    fun `the cap lets 500 writes through and refuses the one past it`() = runTest {
        val completions = kind("completions", rows = 500)
        val engine = engine(listOf(completions))

        engine.syncNow()

        assertEquals(500, space.writes)
        assertTrue(completions.pendingRowIds.isEmpty())
        assertEquals(CloudSyncState.Synced(10_000L), engine.state.value)

        completions.edit("one-more", mapOf("n" to 1L))
        engine.syncNow()

        assertEquals(500, space.writes)
        assertEquals(listOf("one-more"), completions.pendingRowIds)
        assertEquals(CloudSyncState.Paused(PauseReason.WriteCap, 10_000L), engine.state.value)
    }

    @Test
    fun `a batch that would pass the cap is cut at the cap and the rest waits`() = runTest {
        val completions = kind("completions", rows = 300)
        val engine = engine(listOf(completions))
        engine.syncNow()
        assertEquals(300, space.writes)

        repeat(300) { completions.edit("again$it", mapOf("n" to 2L)) }
        engine.syncNow()

        assertEquals(500, space.writes)
        assertEquals(100, completions.pendingRowIds.size)
        assertEquals(listOf("put completions 300", "put completions 200"), log.filter { it.startsWith("put") })
        assertEquals(CloudSyncState.Paused(PauseReason.WriteCap, 10_000L), engine.state.value)
    }

    @Test
    fun `the cap counts across kinds and stops the run after the cut put`() = runTest {
        val completions = kind("completions", rows = 300)
        val drafts = kind("drafts", rows = 300)
        val engine = engine(listOf(completions, drafts, kind("published", rows = 1)))

        engine.syncNow()

        assertEquals(
            listOf("changedSince completions null", "put completions 300", "changedSince drafts null", "put drafts 200"),
            log,
        )
        assertTrue(completions.pendingRowIds.isEmpty())
        assertEquals(100, drafts.pendingRowIds.size)
        assertEquals(CloudSyncState.Paused(PauseReason.WriteCap, null), engine.state.value)
    }

    @Test
    fun `a capped day stays capped for every trigger and starts over the next day`() = runTest {
        val completions = kind("completions", rows = 600)
        val engine = engine(listOf(completions))
        engine.syncNow()
        engine.syncNow()
        engine.syncNow()
        assertEquals(500, space.writes)
        assertEquals(1, log.count { it.startsWith("put") })
        assertEquals(100, completions.pendingRowIds.size)

        clock.ms = DAY + 5_000L
        engine.syncNow()

        assertEquals(600, space.writes)
        assertTrue(completions.pendingRowIds.isEmpty())
        assertEquals(CloudSyncState.Synced(DAY + 5_000L), engine.state.value)
    }

    @Test
    fun `a capped run resumes by itself at the start of the next day`() = runTest {
        val completions = kind("completions", rows = 600)
        val engine = engine(listOf(completions))
        engine.syncNow()
        assertEquals(CloudSyncState.Paused(PauseReason.WriteCap, null), engine.state.value)
        val pullsBefore = pulls
        clock.ms = DAY

        advance(DAY - 10_000L - 1)
        assertEquals(pullsBefore, pulls)
        advance(1)

        assertEquals(pullsBefore + 1, pulls)
        assertEquals(CloudSyncState.Synced(DAY), engine.state.value)
        assertEquals(600, space.writes)
    }

    private companion object {
        const val ANN = "ann"
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
