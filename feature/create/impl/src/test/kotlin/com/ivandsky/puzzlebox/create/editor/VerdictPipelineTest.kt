package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.create.TestDraft
import com.ivandsky.puzzlebox.create.FakeGameEditor
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live verdict (spec A7, ADR-0019 §4): Checking while an edit is pending, the analysis
 * 150 ms after the last edit on the provided default dispatcher, a newer edit cancels the
 * older one (debouncing or already solving), and a solve past 1.5 s settles on Undetermined.
 */
class VerdictPipelineTest {

    private val editor = FakeGameEditor()

    /** Runs on the test scheduler and marks the work it runs, so analysis can tell where it is. */
    private class RecordingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        val running = ThreadLocal.withInitial { false }

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            delegate.dispatch(context, Runnable {
                running.set(true)
                try {
                    block.run()
                } finally {
                    running.set(false)
                }
            })
        }
    }

    /** The test's DispatcherProvider: main and io on the scheduler, default on a [RecordingDispatcher]. */
    private class RecordingDispatcherProvider(override val main: CoroutineDispatcher, val recording: RecordingDispatcher) : DispatcherProvider {
        override val io: CoroutineDispatcher = main
        override val default: CoroutineDispatcher = recording
    }

    private fun TestScope.lanes(): RecordingDispatcherProvider =
        RecordingDispatcherProvider(StandardTestDispatcher(testScheduler), RecordingDispatcher(StandardTestDispatcher(testScheduler)))

    private fun TestScope.pipeline(lanes: RecordingDispatcherProvider, nanoTime: () -> Long = { testScheduler.currentTime * NANOS_PER_MS }) =
        VerdictPipeline(backgroundScope, lanes, nanoTime)

    private fun TestScope.seen(pipeline: VerdictPipeline): List<Verdict> {
        val seen = mutableListOf<Verdict>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { pipeline.verdict.toList(seen) }
        return seen
    }

    /** Runs everything due within a generous window; the pipeline's jobs live in backgroundScope, which advanceUntilIdle ignores. */
    private fun TestScope.settle() {
        advanceTimeBy(SETTLE_MS)
        runCurrent()
    }

    @Test
    fun `analysis waits out the debounce and runs on the default dispatcher`() = runTest {
        val lanes = lanes()
        val onDefault = mutableListOf<Boolean>()
        editor.analysis = { draft, _ ->
            onDefault += checkNotNull(lanes.recording.running.get())
            FakeGameEditor.defaultVerdict(draft)
        }
        val pipeline = pipeline(lanes)

        pipeline.submit(editor.draft(3))
        advanceTimeBy(DEBOUNCE_MS - 1)
        runCurrent()
        assertEquals(Verdict.Checking, pipeline.verdict.value)
        assertTrue("no analysis inside the debounce", editor.analyzed.isEmpty())

        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf(true), onDefault)
        assertEquals(Verdict.Unique(Solution(listOf(3))), pipeline.verdict.value)
    }

    @Test
    fun `a second submit within the debounce cancels the first`() = runTest {
        val pipeline = pipeline(lanes())
        val seen = seen(pipeline)

        pipeline.submit(editor.draft(1))
        advanceTimeBy(DEBOUNCE_MS - 50)
        pipeline.submit(editor.draft(2))
        settle()

        assertEquals(listOf(TestDraft(2)), editor.analyzed)
        assertEquals(listOf(Verdict.Checking, Verdict.Unique(Solution(listOf(2)))), seen)
    }

    @Test
    fun `a newer submit cancels a running solve and its stale verdict never lands`() = runTest {
        lateinit var pipeline: VerdictPipeline
        var staleBudgetEnded = false
        editor.analysis = { draft, budget ->
            if (draft.marks == 1) {
                // The author edits again while this solve is running.
                pipeline.submit(editor.draft(2))
                staleBudgetEnded = budget.exhausted()
                Verdict.Undetermined
            } else {
                FakeGameEditor.defaultVerdict(draft)
            }
        }
        pipeline = pipeline(lanes())
        val seen = seen(pipeline)

        pipeline.submit(editor.draft(1))
        settle()

        assertTrue("the newer submit exhausts the running solve's budget", staleBudgetEnded)
        assertEquals(listOf(TestDraft(1), TestDraft(2)), editor.analyzed)
        assertFalse("the cancelled solve publishes nothing", Verdict.Undetermined in seen)
        assertEquals(Verdict.Unique(Solution(listOf(2))), pipeline.verdict.value)
    }

    @Test
    fun `a solve that never ends settles on Undetermined once the budget is spent`() = runTest {
        var clockMs = 0L
        var polls = 0
        editor.analysis = { _, budget ->
            // Never finds an answer: polls the budget until it says stop.
            while (!budget.exhausted()) polls++
            Verdict.Undetermined
        }
        // Each reading of the clock moves it 1 ms, so the solve "takes" one ms per poll.
        val pipeline = pipeline(lanes(), nanoTime = { clockMs++ * NANOS_PER_MS })

        pipeline.submit(editor.draft(5))
        settle()

        assertEquals(Verdict.Undetermined, pipeline.verdict.value)
        assertTrue("the solve ran for the whole 1.5 s budget, not less: $polls polls", polls >= BUDGET_MS - 1)
        assertTrue("and stopped right after it: $polls polls", polls <= BUDGET_MS)
    }

    @Test
    fun `a board with the same layout key keeps its verdict and is never solved again`() = runTest {
        val pipeline = pipeline(lanes())
        val seen = seen(pipeline)

        pipeline.submit(editor.draft(3))
        settle()
        // A crown-only edit: the board differs only outside layoutOf, so the key is unchanged.
        pipeline.submit(editor.draft(3))
        settle()

        assertEquals(listOf(TestDraft(3)), editor.analyzed)
        assertEquals(listOf(Verdict.Checking, Verdict.Unique(Solution(listOf(3)))), seen)
    }

    @Test
    fun `a same-key submit during the debounce leaves the pending solve to finish`() = runTest {
        val pipeline = pipeline(lanes())

        pipeline.submit(editor.draft(3))
        advanceTimeBy(DEBOUNCE_MS - 50)
        pipeline.submit(editor.draft(3))
        settle()

        assertEquals(listOf(TestDraft(3)), editor.analyzed)
        assertEquals(Verdict.Unique(Solution(listOf(3))), pipeline.verdict.value)
    }

    @Test
    fun `recheck solves the same board again`() = runTest {
        val pipeline = pipeline(lanes())

        pipeline.submit(editor.draft(3))
        settle()
        pipeline.recheck(editor.draft(3))
        assertEquals(Verdict.Checking, pipeline.verdict.value)
        settle()

        assertEquals(listOf(TestDraft(3), TestDraft(3)), editor.analyzed)
        assertEquals(Verdict.Unique(Solution(listOf(3))), pipeline.verdict.value)
    }

    private companion object {
        const val DEBOUNCE_MS = 150L
        const val BUDGET_MS = 1_500
        const val NANOS_PER_MS = 1_000_000L
        const val SETTLE_MS = 10_000L
    }
}
