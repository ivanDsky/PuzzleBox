package com.ivandsky.puzzlebox.create

import com.ivandsky.puzzlebox.game.editor.Budget
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Custom list analyses every draft on `dispatchers.default`, so with real dispatchers two rows
 * call [FakeGameEditor.analyze] from two threads at once (#2086: `CustomBodyImportTest` seeds two
 * drafts and flaked once with an `ArrayIndexOutOfBoundsException` in `ArrayList.add`).
 */
class EditorAnalyzeConcurrencyTest {

    @Test
    fun `concurrent analyses are all recorded and none throws`() {
        val editor = FakeGameEditor()
        val threads = 8
        val perThread = 1_000
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        try {
            val done = (0 until threads).map { t ->
                pool.submit {
                    start.await()
                    try {
                        repeat(perThread) { editor.analyze(TestDraft(t + 1), Budget.Unlimited) }
                    } catch (e: Throwable) {
                        failure.compareAndSet(null, e)
                    }
                }
            }
            start.countDown()
            done.forEach { it.get(60, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertNull("an analysis threw: ${failure.get()}", failure.get())
        assertEquals(threads * perThread, editor.analyzed.size)
        assertTrue(editor.analyzed.all { it.marks in 1..threads })
    }
}
