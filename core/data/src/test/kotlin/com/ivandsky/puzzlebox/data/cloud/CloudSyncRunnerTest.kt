package com.ivandsky.puzzlebox.data.cloud

import android.app.Application
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import com.ivandsky.puzzlebox.testing.network.FakeNetworkStatus
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The runner's trigger table (ticket 15) on a virtual clock: each trigger starts one run, a burst of pending changes
 * within 2 s starts one, a pending set that did not grow starts none, signed out starts none. Robolectric: a failed
 * run is logged through android.util.Log.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CloudSyncRunnerTest {

    /** A kind whose pending ids the test pushes by hand; every uid the runner asked for lands in [asked]. */
    private class PendingKind(name: String) : SyncedKind by RecordingKind(name) {
        val pending = MutableSharedFlow<Set<String>>(replay = 1, extraBufferCapacity = 16)
        val asked = mutableListOf<String>()

        override fun pendingIds(uid: String): Flow<Set<String>> {
            asked += uid
            return pending
        }

        fun set(vararg ids: String) {
            check(pending.tryEmit(ids.toSet()))
        }
    }

    private val ann = Identity(uid = "ann", displayName = "Ann", photoUrl = null)
    private val bob = Identity(uid = "bob", displayName = "Bob", photoUrl = null)
    private val identity = FakeIdentityService(ann)
    private val network = FakeNetworkStatus(initial = true)
    private val engine = FakeCloudSync()
    private val drafts = PendingKind("drafts")
    private val completions = PendingKind("completions")

    private fun TestScope.start(engine: CloudSync = this@CloudSyncRunnerTest.engine) {
        drafts.set()
        completions.set()
        CloudSyncRunner(engine, listOf(completions, drafts), identity, network, backgroundScope).start()
        runCurrent()
    }

    private suspend fun TestScope.pass(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun `app start with a signed-in account starts one run`() = runTest {
        start()
        pass(10_000)

        assertEquals(1, engine.syncNowCalls)
    }

    @Test
    fun `signed out starts no run on start, network edge or pending change`() = runTest {
        identity.currentIdentity = null
        start()

        network.setOnline(false)
        runCurrent()
        network.setOnline(true)
        runCurrent()
        drafts.set("d1")
        pass(10_000)

        assertEquals(0, engine.syncNowCalls)
        assertTrue(drafts.asked.isEmpty())
    }

    @Test
    fun `signing in starts one run, switching account one more, signing out none`() = runTest {
        identity.currentIdentity = null
        start()

        identity.currentIdentity = ann
        runCurrent()
        assertEquals(1, engine.syncNowCalls)

        identity.currentIdentity = bob
        runCurrent()
        assertEquals(2, engine.syncNowCalls)

        identity.currentIdentity = null
        pass(10_000)
        assertEquals(2, engine.syncNowCalls)
    }

    @Test
    fun `the network coming back starts one run, going away or staying online none`() = runTest {
        start()
        assertEquals(1, engine.syncNowCalls)

        network.setOnline(false)
        runCurrent()
        assertEquals(1, engine.syncNowCalls)

        network.setOnline(true)
        runCurrent()
        assertEquals(2, engine.syncNowCalls)

        network.setOnline(true)
        pass(10_000)
        assertEquals(2, engine.syncNowCalls)
    }

    @Test
    fun `starting offline runs once and the network edge runs again`() = runTest {
        network.setOnline(false)
        start()
        assertEquals(1, engine.syncNowCalls)

        network.setOnline(true)
        runCurrent()
        assertEquals(2, engine.syncNowCalls)
    }

    @Test
    fun `a pending change starts one run after 2 seconds`() = runTest {
        start()
        assertEquals(1, engine.syncNowCalls)

        drafts.set("d1")
        pass(1_999)
        assertEquals(1, engine.syncNowCalls)

        pass(1)
        assertEquals(2, engine.syncNowCalls)
    }

    @Test
    fun `a burst of pending changes within 2 seconds starts one run`() = runTest {
        start()

        drafts.set("d1")
        pass(1_000)
        drafts.set("d1", "d2")
        pass(1_000)
        completions.set("c1")
        pass(1_999)
        assertEquals(1, engine.syncNowCalls)

        pass(1)
        assertEquals(2, engine.syncNowCalls)
        pass(10_000)
        assertEquals(2, engine.syncNowCalls)
    }

    @Test
    fun `a pending set that did not change starts no run`() = runTest {
        start()
        drafts.set("d1")
        pass(10_000)
        assertEquals(2, engine.syncNowCalls)

        drafts.set("d1")
        drafts.set("d1")
        completions.set()
        pass(10_000)

        assertEquals(2, engine.syncNowCalls)
    }

    @Test
    fun `a pending set that only shrinks starts no run`() = runTest {
        start()
        drafts.set("d1", "d2")
        pass(10_000)
        assertEquals(2, engine.syncNowCalls)

        drafts.set("d2")
        pass(10_000)
        drafts.set()
        pass(10_000)

        assertEquals(2, engine.syncNowCalls)
    }

    @Test
    fun `pending ids are watched for the signed-in uid and again for the next one`() = runTest {
        start()
        assertEquals(listOf("ann"), drafts.asked)
        assertEquals(listOf("ann"), completions.asked)

        identity.currentIdentity = bob
        runCurrent()

        assertEquals(listOf("ann", "bob"), drafts.asked)
        assertEquals(listOf("ann", "bob"), completions.asked)
    }

    @Test
    fun `a pending change under one account is not carried over as a change under the next`() = runTest {
        start()
        drafts.set("d1")

        identity.currentIdentity = bob
        runCurrent()
        assertEquals(2, engine.syncNowCalls)
        pass(10_000)

        assertEquals(2, engine.syncNowCalls)
    }

    @Test
    fun `signing out cancels nothing in flight`() = runTest {
        val release = CompletableDeferred<Unit>()
        var cancelled = false
        var finished = false
        val hanging = object : CloudSync by engine {
            override suspend fun syncNow() {
                try {
                    release.await()
                    finished = true
                } catch (e: kotlinx.coroutines.CancellationException) {
                    cancelled = true
                    throw e
                }
            }
        }
        start(hanging)

        identity.currentIdentity = null
        pass(10_000)
        assertFalse(cancelled)
        assertFalse(finished)

        release.complete(Unit)
        runCurrent()
        assertFalse(cancelled)
        assertTrue(finished)
    }

    @Test
    fun `a failed run does not stop later triggers`() = runTest {
        var calls = 0
        val failing = object : CloudSync by engine {
            override suspend fun syncNow() {
                calls++
                error("boom")
            }
        }
        start(failing)
        assertEquals(1, calls)

        network.setOnline(false)
        runCurrent()
        network.setOnline(true)
        runCurrent()
        drafts.set("d1")
        pass(2_000)

        assertEquals(3, calls)
    }
}
