package com.ivandsky.puzzlebox.data.linkedin

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A day is sent at most once: taps that overlap share one post, and the post outlives the screen that asked. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LinkedInPostFlowSingleFlightTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var rig: LinkedInPostRig? = null
    private val onTheWire = CompletableDeferred<Unit>()
    private val answer = CompletableDeferred<Unit>()

    @After
    fun tearDown() {
        rig?.close()
    }

    /** A postable day whose send stays on the wire until [answer] completes. */
    private suspend fun TestScope.heldRig(): LinkedInPostRig {
        val rig = LinkedInPostRig(this, folder.root).also { rig = it }
        rig.postable()
        rig.poster.onSend = {
            onTheWire.complete(Unit)
            answer.await()
        }
        return rig
    }

    @Test
    fun `taps that overlap share one check and one post, and all hear its outcome`() = runTest {
        val rig = heldRig()

        val taps = List(3) { async { rig.submit() } }
        onTheWire.await()
        val late = async { rig.submit() }
        testScheduler.runCurrent()
        answer.complete(Unit)

        assertEquals(List(4) { LinkedInPostState.Posted }, (taps + late).awaitAll())
        assertEquals(1, rig.sends)
        assertEquals(listOf("own:me:6:712"), rig.source.calls)
    }

    @Test
    fun `a tap after the post landed sends nothing more`() = runTest {
        val rig = heldRig()
        answer.complete(Unit)
        rig.submit()

        val again = rig.submit()

        assertEquals(LinkedInPostState.Posted, again)
        assertEquals(1, rig.sends)
        assertEquals(listOf("own:me:6:712"), rig.source.calls)
    }

    @Test
    fun `a post goes on and is remembered after the screen that asked is gone`() = runTest(timeout = 10.seconds) {
        val rig = heldRig()
        val screen = launch { rig.submit() }
        onTheWire.await()

        screen.cancelAndJoin()
        answer.complete(Unit)
        rig.posts.observe(rig.game, rig.today).first { it.state == LinkedInPostState.Posted }

        assertEquals(setOf("fixture/2026-11-05"), rig.stored().postedRefs)
        assertEquals(emptySet<String>(), rig.stored().sendingRefs)
    }

    @Test
    fun `a tap that joins after the first one left still gets the one post's outcome`() = runTest {
        val rig = heldRig()
        val first = launch { rig.submit() }
        onTheWire.await()
        first.cancelAndJoin()

        val second = async { rig.submit() }
        testScheduler.runCurrent()
        answer.complete(Unit)

        assertEquals(LinkedInPostState.Posted, second.await())
        assertEquals(1, rig.sends)
    }

    @Test
    fun `a post that throws fails its tap and lets the next tap run`() = runTest {
        val rig = LinkedInPostRig(this, folder.root).also { rig = it }
        rig.postable()
        rig.poster.onSend = { throw IllegalStateException("socket closed") }

        val failure = runCatching { rig.submit() }.exceptionOrNull()
        rig.poster.onSend = {}
        val next = rig.submit()

        assertEquals("socket closed", failure?.message)
        assertEquals(LinkedInPostState.Posted, next)
        assertEquals(2, rig.sends)
    }
}
