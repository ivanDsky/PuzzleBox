package com.ivandsky.puzzlebox.data.cloud

import app.cash.turbine.test
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSpace
import com.ivandsky.puzzlebox.testing.cloud.FakeSyncStateStore
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The engine's one run (ticket 12) over [RecordingKind]s, [FakeCloudSpace] and [FakeSyncStateStore], on virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncedKindsCloudSyncTest {

    private class MutableClock(var ms: Long) : Clock() {
        override fun instant(): Instant = Instant.ofEpochMilli(ms)
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }

    /** Appends every call to [log] before delegating, so a test sees the cloud calls between the kinds' calls. */
    private class LoggingSpace(private val delegate: CloudSpace, private val log: MutableList<String>) : CloudSpace {
        override suspend fun changedSince(uid: String, kind: String, cursorMs: Long?): CloudResult<List<CloudDoc>> {
            log += "changedSince $kind $uid $cursorMs"
            return delegate.changedSince(uid, kind, cursorMs)
        }

        override suspend fun put(uid: String, kind: String, docs: List<CloudDoc>): CloudResult<Unit> {
            log += "put $kind $uid ${docs.joinToString(",") { it.id }}"
            return delegate.put(uid, kind, docs)
        }

        override suspend fun delete(uid: String, kind: String, ids: List<String>): CloudResult<Unit> {
            log += "delete $kind $uid ${ids.joinToString(",")}"
            return delegate.delete(uid, kind, ids)
        }

        override suspend fun deleteAll(uid: String, kinds: List<String>): CloudResult<Unit> {
            log += "deleteAll $uid ${kinds.joinToString(",")}"
            return delegate.deleteAll(uid, kinds)
        }
    }

    private val ann = Identity(uid = ANN, displayName = "Ann", photoUrl = null)
    private val bob = Identity(uid = BOB, displayName = "Bob", photoUrl = null)
    private val identity = FakeIdentityService(ann)
    private var cloudNow = 1_000L
    private val space = FakeCloudSpace(identity) { cloudNow }
    private val store = FakeSyncStateStore(lastUid = LAST)
    private val clock = MutableClock(10_000L)
    private val log = mutableListOf<String>()
    private val loggingSpace = LoggingSpace(space, log)

    private fun kind(name: String) = RecordingKind(name, events = log)

    private fun TestScope.engine(
        kinds: List<SyncedKind>,
        scope: CoroutineScope = backgroundScope,
    ): SyncedKindsCloudSync {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val main = dispatcher
            override val io = dispatcher
            override val default = dispatcher
        }
        return SyncedKindsCloudSync(
            kinds, loggingSpace, store, identity, CloudErase(loggingSpace, kinds, store, identity), clock, dispatchers, scope,
        )
    }

    /** Seeds [uid]'s cloud space directly; the fake takes the writer from the identity, so [uid] signs in for the write. */
    private suspend fun cloud(uid: String, kind: String, vararg ids: String) {
        val signedIn = identity.currentIdentity
        identity.currentIdentity = if (uid == ANN) ann else bob
        val put = space.put(uid, kind, ids.map { CloudDoc(it, mapOf("n" to 1L)) })
        identity.currentIdentity = signedIn
        assertEquals(CloudResult.Ok(Unit), put)
    }

    @Test
    fun `signed out publishes SignedOut and makes no call`() = runTest {
        identity.currentIdentity = null
        val completions = kind("completions")
        val engine = engine(listOf(completions))

        engine.syncNow()

        assertEquals(CloudSyncState.SignedOut, engine.state.value)
        assertTrue(log.isEmpty())
        assertEquals(0, space.reads + space.writes + space.deletes)
    }

    @Test
    fun `signing out afterwards turns the state to SignedOut`() = runTest {
        val engine = engine(listOf(kind("completions")))
        engine.syncNow()
        assertEquals(CloudSyncState.Synced(10_000L), engine.state.value)

        identity.currentIdentity = null
        runCurrent()

        assertEquals(CloudSyncState.SignedOut, engine.state.value)
    }

    @Test
    fun `adopts every kind first then per kind pulls before it pushes`() = runTest {
        val completions = kind("completions")
        val drafts = kind("drafts")
        completions.edit("g1", mapOf("n" to 1L))
        drafts.edit("d1", mapOf("n" to 2L))
        cloud(ANN, "completions", "g9")
        val engine = engine(listOf(completions, drafts))

        engine.syncNow()

        assertEquals(
            listOf(
                "adopt completions ann",
                "adopt drafts ann",
                "changedSince completions ann null",
                "apply completions ann g9",
                "pending completions ann",
                "put completions ann g1",
                "acknowledge completions ann g1",
                "changedSince drafts ann null",
                "pending drafts ann",
                "put drafts ann d1",
                "acknowledge drafts ann d1",
            ),
            log,
        )
        assertEquals(CloudSyncState.Synced(10_000L), engine.state.value)
    }

    @Test
    fun `nothing pending means no put`() = runTest {
        val engine = engine(listOf(kind("completions")))

        engine.syncNow()

        assertEquals(0, space.writes)
        assertTrue(log.none { it.startsWith("put") })
    }

    @Test
    fun `an offline put is not acknowledged and the next run sends it again`() = runTest { ackOnlyOnOk(CloudResult.Offline) }

    @Test
    fun `a denied put is not acknowledged and the next run sends it again`() = runTest { ackOnlyOnOk(CloudResult.Denied) }

    @Test
    fun `an exhausted put is not acknowledged and the next run sends it again`() = runTest { ackOnlyOnOk(CloudResult.Exhausted) }

    private suspend fun TestScope.ackOnlyOnOk(failure: CloudResult<Nothing>) {
        val completions = kind("completions")
        completions.edit("g1", mapOf("n" to 1L))
        completions.onCall = { if (it == "pending") space.failNext(failure) }
        val engine = engine(listOf(completions))

        engine.syncNow()

        assertTrue(completions.calls("acknowledge").isEmpty())
        assertEquals(listOf("g1"), completions.pendingRowIds)
        assertTrue(space.docs(ANN, "completions").isEmpty())

        completions.onCall = {}
        engine.syncNow()

        assertEquals(listOf("acknowledge completions ann g1"), completions.calls("acknowledge"))
        assertTrue(completions.pendingRowIds.isEmpty())
        assertEquals(listOf("g1"), space.docs(ANN, "completions").map { it.id })
    }

    @Test
    fun `the cursor advances to the newest stamp seen and the next pull starts there`() = runTest {
        val completions = kind("completions")
        val drafts = kind("drafts")
        cloudNow = 1_000L
        cloud(ANN, "completions", "g1")
        cloudNow = 2_000L
        cloud(ANN, "completions", "g2")
        val engine = engine(listOf(completions, drafts))

        engine.syncNow()

        assertEquals(2_000L, store.cursor(ANN, "completions"))
        assertNull(store.cursor(ANN, "drafts"))
        cloudNow = 3_000L
        cloud(ANN, "completions", "g3")

        engine.syncNow()

        assertEquals(listOf("changedSince completions ann null", "changedSince completions ann 2000"), log.filter { it.startsWith("changedSince completions") })
        assertEquals(3_000L, store.cursor(ANN, "completions"))
        assertEquals(listOf("g1", "g2", "g3"), completions.rows)
    }

    @Test
    fun `two concurrent syncNow share one run`() = runTest {
        val completions = kind("completions")
        val engine = engine(listOf(completions))

        joinAll(launch { engine.syncNow() }, launch { engine.syncNow() })

        assertEquals(1, completions.calls("adopt").size)
    }

    @Test
    fun `syncNow calls during a run queue exactly one more run`() = runTest {
        val completions = kind("completions")
        val engine = engine(listOf(completions))
        val callers = mutableListOf<Job>()
        var fired = false
        completions.onCall = {
            if (it == "pending" && !fired) {
                fired = true
                repeat(3) { callers += launch { engine.syncNow() } }
                yield()
            }
        }

        engine.syncNow()
        joinAll(*callers.toTypedArray())

        assertEquals(2, completions.calls("adopt").size)
        assertEquals(2, log.count { it.startsWith("changedSince") })
        assertEquals(CloudSyncState.Synced(10_000L), engine.state.value)
    }

    @Test
    fun `state is Syncing with firstRestore until the account's first run ends`() = runTest {
        val completions = kind("completions")
        val drafts = kind("drafts")
        cloud(ANN, "completions", "g1")
        val engine = engine(listOf(completions, drafts))
        val seen = mutableListOf<CloudSyncState>()
        completions.onCall = { if (it == "pending") seen += engine.state.value }

        engine.syncNow()
        clock.ms = 20_000L
        engine.syncNow()

        assertEquals(listOf(CloudSyncState.Syncing(true), CloudSyncState.Syncing(false)), seen)
        assertEquals(CloudSyncState.Synced(20_000L), engine.state.value)
    }

    @Test
    fun `a uid flip mid-run commits nothing under the new uid and does no further call`() = runTest {
        val completions = kind("completions")
        val drafts = kind("drafts")
        completions.edit("mine", mapOf("n" to 1L))
        cloud(ANN, "completions", "g1")
        completions.onCall = { if (it == "apply") identity.currentIdentity = bob }
        val engine = engine(listOf(completions, drafts))

        engine.syncNow()

        assertTrue(log.none { it.contains(" $BOB") })
        assertEquals("apply completions ann g1", log.last())
        assertTrue(drafts.calls("pending").isEmpty() && drafts.calls("apply").isEmpty())
        assertTrue(log.none { it.startsWith("put") })
        assertNull(store.cursor(BOB, "completions"))
        assertFalse(store.restoreShown(BOB))
        assertFalse(store.restoreShown(ANN))
        assertTrue(space.isEmpty(BOB))
        assertFalse(engine.state.value is CloudSyncState.Synced)
    }

    @Test
    fun `a uid flip during adopt stops before the next kind adopts`() = runTest {
        val completions = kind("completions")
        val drafts = kind("drafts")
        completions.onCall = { if (it == "adopt") identity.currentIdentity = bob }
        val engine = engine(listOf(completions, drafts))

        engine.syncNow()

        assertEquals(listOf("adopt completions ann"), log)
    }

    @Test
    fun `signing out mid-run ends the run in SignedOut`() = runTest {
        val completions = kind("completions")
        cloud(ANN, "completions", "g1")
        completions.onCall = { if (it == "apply") identity.currentIdentity = null }
        val engine = engine(listOf(completions))

        engine.syncNow()

        assertEquals(CloudSyncState.SignedOut, engine.state.value)
        assertTrue(completions.calls("pending").isEmpty())
    }

    @Test
    fun `wrong account on a pull ends the run at once with no cursor or marker change`() = runTest {
        val completions = kind("completions")
        val drafts = kind("drafts")
        completions.edit("g1", mapOf("n" to 1L))
        space.failNext(CloudResult.WrongAccount)
        val engine = engine(listOf(completions, drafts))

        engine.syncNow()

        assertEquals(
            listOf("adopt completions ann", "adopt drafts ann", "changedSince completions ann null"),
            log,
        )
        assertNull(store.cursor(ANN, "completions"))
        assertFalse(store.restoreShown(ANN))
        assertEquals(listOf("g1"), completions.pendingRowIds)
        assertFalse(engine.state.value is CloudSyncState.Synced)
    }

    @Test
    fun `wrong account on a put ends the run at once and acknowledges nothing`() = runTest {
        val completions = kind("completions")
        val drafts = kind("drafts")
        completions.edit("g1", mapOf("n" to 1L))
        completions.onCall = { if (it == "pending") space.failNext(CloudResult.WrongAccount) }
        val engine = engine(listOf(completions, drafts))

        engine.syncNow()

        assertEquals("put completions ann g1", log.last())
        assertTrue(completions.calls("acknowledge").isEmpty())
        assertTrue(drafts.calls("pending").isEmpty())
        assertEquals(listOf("g1"), completions.pendingRowIds)
        assertFalse(store.restoreShown(ANN))
        assertFalse(engine.state.value is CloudSyncState.Synced)
    }

    @Test
    fun `any other failure stops the run and keeps the previous state`() = runTest {
        val completions = kind("completions")
        val drafts = kind("drafts")
        val engine = engine(listOf(completions, drafts))
        engine.syncNow()
        assertEquals(CloudSyncState.Synced(10_000L), engine.state.value)
        log.clear()
        clock.ms = 50_000L
        space.failNext(CloudResult.Offline)

        engine.syncNow()

        assertEquals(CloudSyncState.Synced(10_000L), engine.state.value)
        assertEquals(listOf("adopt completions ann", "adopt drafts ann", "changedSince completions ann null"), log)
    }

    @Test
    fun `the restore summary is emitted once for an account's first restore`() = runTest {
        cloud(ANN, "completions", "g1", "g2")
        cloud(ANN, "drafts", "d1")
        cloud(ANN, "boards", "b1", "b2", "b3")
        cloud(ANN, "published", "p1", "p2", "p3", "p4", "p5")
        val engine = engine(listOf(kind("completions"), kind("drafts"), kind("published"), kind("boards")))

        engine.restored.test {
            engine.syncNow()
            assertEquals(RestoreSummary(games = 2, drafts = 1, boardDays = 3), awaitItem())
            assertTrue(store.restoreShown(ANN))

            engine.syncNow()
            cloudNow = 2_000L
            cloud(ANN, "completions", "g3")
            engine.syncNow()
            expectNoEvents()
        }
    }

    @Test
    fun `each account gets its own restore summary`() = runTest {
        cloud(ANN, "drafts", "d1")
        cloud(BOB, "drafts", "d2", "d3")
        val engine = engine(listOf(kind("drafts")))

        engine.restored.test {
            engine.syncNow()
            assertEquals(RestoreSummary(0, 1, 0), awaitItem())

            identity.currentIdentity = bob
            engine.syncNow()
            assertEquals(RestoreSummary(0, 2, 0), awaitItem())

            identity.currentIdentity = ann
            engine.syncNow()
            expectNoEvents()
        }
    }

    @Test
    fun `an empty first restore emits nothing but is not repeated`() = runTest {
        val engine = engine(listOf(kind("completions")))

        engine.restored.test {
            engine.syncNow()
            expectNoEvents()
            assertTrue(store.restoreShown(ANN))
        }
    }

    @Test
    fun `a first restore that failed halfway reports what the runs together brought`() = runTest {
        cloud(ANN, "completions", "g1", "g2")
        cloud(ANN, "drafts", "d1")
        val completions = kind("completions")
        val drafts = kind("drafts")
        completions.onCall = { if (it == "pending") space.failNext(CloudResult.Offline) }
        val engine = engine(listOf(completions, drafts))

        engine.restored.test {
            engine.syncNow()
            expectNoEvents()
            assertFalse(store.restoreShown(ANN))

            completions.onCall = {}
            engine.syncNow()
            assertEquals(RestoreSummary(games = 2, drafts = 1, boardDays = 0), awaitItem())
        }
    }

    @Test
    fun `a restart with a pending acknowledge sends the row again without a duplicate`() = runTest {
        val completions = kind("completions")
        completions.edit("g1", mapOf("n" to 1L))
        completions.onCall = { if (it == "acknowledge") awaitCancellation() }
        val dying = Job()
        val first = engine(listOf(completions), CoroutineScope(StandardTestDispatcher(testScheduler) + dying))
        val caller = launch { runCatching { first.syncNow() } }
        runCurrent()

        assertEquals(listOf("g1"), space.docs(ANN, "completions").map { it.id })
        assertEquals(listOf("g1"), completions.pendingRowIds)

        dying.cancel()
        caller.join()
        completions.onCall = {}
        val second = engine(listOf(completions))
        second.syncNow()

        assertEquals(listOf("g1"), space.docs(ANN, "completions").map { it.id })
        assertEquals(listOf("g1", "g1"), space.payloads.map { it.doc.id })
        assertTrue(completions.pendingRowIds.isEmpty())
        assertEquals(CloudSyncState.Synced(10_000L), second.state.value)
    }

    @Test
    fun `eraseCloud waits for the run in flight`() = runTest {
        val completions = kind("completions")
        completions.edit("g1", mapOf("n" to 1L))
        val engine = engine(listOf(completions))
        var erased: CloudResult<Unit>? = null
        var fired = false
        completions.onCall = {
            if (it == "pending" && !fired) {
                fired = true
                launch { erased = engine.eraseCloud() }
                yield()
            }
        }

        engine.syncNow()
        runCurrent()

        assertTrue(log.indexOf("deleteAll ann completions") > log.indexOf("acknowledge completions ann g1"))
        assertEquals(CloudResult.Ok(Unit), erased)
        assertTrue(space.isEmpty(ANN))
        assertNull(identity.currentIdentity)
        assertEquals(CloudSyncState.SignedOut, engine.state.value)
    }

    @Test
    fun `eraseCloud while signed out answers wrong account and calls nothing`() = runTest {
        identity.currentIdentity = null
        val engine = engine(listOf(kind("completions")))

        assertEquals(CloudResult.WrongAccount, engine.eraseCloud())
        assertTrue(log.isEmpty())
    }

    @Test
    fun `a failed erase leaves the state and the account alone`() = runTest {
        val engine = engine(listOf(kind("completions")))
        engine.syncNow()
        space.failNext(CloudResult.Denied)

        assertEquals(CloudResult.Denied, engine.eraseCloud())

        assertEquals(CloudSyncState.Synced(10_000L), engine.state.value)
        assertEquals(ANN, identity.currentIdentity?.uid)
    }

    @Test
    fun `an erase makes the account's next restore a first restore again`() = runTest {
        cloud(ANN, "drafts", "d1")
        val drafts = kind("drafts")
        val engine = engine(listOf(drafts))
        engine.syncNow()
        engine.eraseCloud()
        identity.currentIdentity = ann
        val seen = mutableListOf<CloudSyncState>()
        drafts.onCall = { if (it == "pending") seen += engine.state.value }

        engine.syncNow()

        assertEquals(listOf(CloudSyncState.Syncing(true)), seen)
        assertEquals(listOf("d1"), space.docs(ANN, "drafts").map { it.id })
    }

    @Test
    fun `the engine never writes the last uid`() = runTest {
        val engine = engine(listOf(kind("completions")))
        engine.syncNow()

        identity.currentIdentity = bob
        engine.syncNow()

        assertEquals(LAST, store.lastUid.value)
    }

    private companion object {
        const val ANN = "ann"
        const val BOB = "bob"
        const val LAST = "zed"
    }
}
