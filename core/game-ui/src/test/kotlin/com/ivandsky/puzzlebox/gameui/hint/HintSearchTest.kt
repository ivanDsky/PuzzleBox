package com.ivandsky.puzzlebox.gameui.hint

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec A19/A10: compute runs on the compute dispatcher and deliver on the scope's; a newer request
 * or an invalidate() drops a result not yet delivered; a delivered result arrives once; a throwing
 * compute surfaces on the scope's handler, unswallowed.
 *
 * The scope runs on a StandardTestDispatcher drained by the test thread, standing in for main.
 * Compute runs on a real two-thread pool, so a compute can block on a latch mid-flight without
 * blocking "main", and a first compute can finish after a second one.
 */
class HintSearchTest {

    private val scheduler = TestCoroutineScheduler()
    private val errors = CopyOnWriteArrayList<Throwable>()
    private val scope = CoroutineScope(
        SupervisorJob() + StandardTestDispatcher(scheduler, name = "main") +
            CoroutineExceptionHandler { _, error -> errors += error },
    )
    private val poolThreads = CopyOnWriteArrayList<Thread>()
    private val pool = Executors.newFixedThreadPool(2) { task -> Thread(task, "hint-compute").also { poolThreads += it } }
    private val search = HintSearch(scope, pool.asCoroutineDispatcher())

    /** Written only by deliver, which runs on the test thread inside runCurrent(). */
    private val delivered = mutableListOf<String>()

    @After
    fun tearDown() {
        scope.cancel()
        pool.shutdownNow()
    }

    @Test
    fun `compute runs on the compute dispatcher and deliver on the scope's`() {
        val main = Thread.currentThread()
        var computedOn: Thread? = null
        var deliveredOn: Thread? = null

        search.request(compute = { computedOn = Thread.currentThread() }, deliver = { deliveredOn = Thread.currentThread() })
        settle()

        // Compared by identity: coroutine debug mode suffixes a running thread's name with its coroutine.
        assertTrue("compute ran on ${computedOn?.name}", computedOn in poolThreads)
        assertSame(main, deliveredOn)
    }

    @Test
    fun `a newer request drops the older result even when the older compute finishes later`() {
        val firstStarted = CountDownLatch(1)
        val firstGate = CountDownLatch(1)
        val secondComputed = CountDownLatch(1)

        search.request(compute = { firstStarted.countDown(); firstGate.awaitOrFail(); "first" }, deliver = { delivered += it })
        scheduler.runCurrent()
        firstStarted.awaitOrFail()
        search.request(compute = { "second".also { secondComputed.countDown() } }, deliver = { delivered += it })
        scheduler.runCurrent()
        secondComputed.awaitOrFail()
        firstGate.countDown()
        settle()

        assertEquals(listOf("second"), delivered)
    }

    @Test
    fun `invalidate before compute starts delivers nothing`() {
        search.request(compute = { "stale" }, deliver = { delivered += it })
        search.invalidate()
        settle()

        assertEquals(emptyList<String>(), delivered)
    }

    @Test
    fun `invalidate while compute is mid-flight delivers nothing once it finishes`() {
        val started = CountDownLatch(1)
        val gate = CountDownLatch(1)

        search.request(compute = { started.countDown(); gate.awaitOrFail(); "stale" }, deliver = { delivered += it })
        scheduler.runCurrent()
        started.awaitOrFail()
        search.invalidate()
        gate.countDown()
        settle()

        assertEquals(emptyList<String>(), delivered)
    }

    @Test
    fun `invalidate after compute finished but before deliver ran delivers nothing`() {
        search.request(compute = { "stale" }, deliver = { delivered += it })
        scheduler.runCurrent()
        finishCompute()
        search.invalidate()
        scheduler.runCurrent()

        assertEquals(emptyList<String>(), delivered)
    }

    @Test
    fun `invalidate with nothing pending is a no-op`() {
        search.invalidate()
        search.request(compute = { "fresh" }, deliver = { delivered += it })
        settle()

        assertEquals(listOf("fresh"), delivered)
    }

    @Test
    fun `a request after an invalidate still delivers`() {
        search.request(compute = { "stale" }, deliver = { delivered += it })
        search.invalidate()
        search.request(compute = { "fresh" }, deliver = { delivered += it })
        settle()

        assertEquals(listOf("fresh"), delivered)
    }

    @Test
    fun `a request delivers its computed value exactly once`() {
        var computations = 0

        search.request(compute = { computations++; "only" }, deliver = { delivered += it })
        settle()
        search.invalidate()
        scheduler.runCurrent()

        assertEquals(listOf("only"), delivered)
        assertEquals(1, computations)
    }

    @Test
    fun `a throwing compute surfaces on the scope's handler and delivers nothing`() {
        search.request<String>(compute = { error("boom") }, deliver = { delivered += it })
        settle()

        assertEquals(emptyList<String>(), delivered)
        assertEquals("boom", errors.single().message)
    }

    /** Runs the launched body, lets every compute finish, then runs any delivery. Ends the pool: call once. */
    private fun settle() {
        scheduler.runCurrent()
        finishCompute()
        scheduler.runCurrent()
    }

    /** Waits for every queued compute; its hand-back to the scope is enqueued before the pool thread ends. */
    private fun finishCompute() {
        pool.shutdown()
        assertTrue("compute did not finish", pool.awaitTermination(TIMEOUT_SECONDS, SECONDS))
    }

    private fun CountDownLatch.awaitOrFail() {
        check(await(TIMEOUT_SECONDS, SECONDS)) { "latch timed out" }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 5L
    }
}
