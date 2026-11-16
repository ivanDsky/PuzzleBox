package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.create.FakeDraftCompleter
import com.ivandsky.puzzlebox.create.TestDraft
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.FinishBudget
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E1's budgeted run (spec A2, A3) against the fake completer: a press lands one undoable
 * completion; the next press completes the author's own pieces again; a spent budget or a
 * give-up leaves the board and reports the pieces it could not finish; a second tap, an edit and
 * leaving cancel it. A press's level reaches the completer and sets how long it may take (90).
 */
class FinishRunTest {

    private val editor = FakeDraftCompleter()
    private val pieces = editor.draft(marks = 2)

    /** The pieces each give-up was reported with, in order. */
    private val gaveUp = mutableListOf<EditorDraft<*>>()

    /** What the strip's press lands on the board, in order. */
    private val landed = mutableListOf<EditorDraft<*>>()

    private fun TestScope.run(
        default: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
        scope: CoroutineScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job()),
        nanoTime: () -> Long = System::nanoTime,
        budgetMs: (Difficulty.Level?) -> Long = FinishBudget::millisFor,
    ): FinishRun {
        val lanes = object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Main
            override val io: CoroutineDispatcher = default
            override val default: CoroutineDispatcher = default
        }
        return FinishRun(scope, lanes, Random(SEED), gaveUp::add, nanoTime, budgetMs)
    }

    /** The first [count] completions' marks added to [base], from the seed the run is given. */
    private fun added(count: Int): List<Int> = Random(SEED).let { random -> List(count) { random.nextInt(1, 10) } }

    @Test
    fun `a press lands one completion that keeps the pieces and undoes in one step`() = runTest {
        val run = run()
        run.press(pieces, landed::add)
        assertTrue("busy while the completion runs", run.busy.value)
        runCurrent()

        val completion = landed.single()
        assertEquals(TestDraft(2 + added(1).single()), completion.state.draft)
        assertFalse(run.busy.value)
        assertTrue(run.repeating.value)
        // One step: undo is the author's pieces, and one more is the start.
        val undone = completion.undo()!!
        assertEquals(TestDraft(2), undone.state.draft)
        assertNull(undone.undo())
        assertTrue(gaveUp.isEmpty())
    }

    @Test
    fun `a second press completes the original pieces again, not the previous completion`() = runTest {
        val run = run()
        run.press(pieces, landed::add)
        runCurrent()
        val first = landed.single()

        run.press(first, landed::add)
        runCurrent()

        assertEquals("both completions started from the author's pieces", listOf(TestDraft(2), TestDraft(2)), editor.completed)
        val (a, b) = added(2)
        assertEquals(TestDraft(2 + b), landed.last().state.draft)
        // Undo goes back to the previous completion, then to the pieces.
        assertEquals(TestDraft(2 + a), landed.last().undo()!!.state.draft)
        assertEquals(TestDraft(2), landed.last().undo()!!.undo()!!.state.draft)
        assertTrue(run.repeating.value)
    }

    @Test
    fun `a budget that runs out leaves the board unchanged and raises the message`() = runTest {
        var polls = 0
        editor.completionOf = { _, _, budget ->
            while (!budget.exhausted()) polls++
            Completion.GaveUp
        }
        var clock = 0L
        // Every read of the clock is one second: the budget starts at 1 s, its first poll reads 2 s (1 s in), its second 3 s (2 s in).
        val run = run(nanoTime = { clock += ONE_SECOND_NANOS; clock })
        run.press(pieces, landed::add)
        runCurrent()

        assertEquals("the budget is 2 s: one poll still inside it, the next spent", 1, polls)
        assertTrue(landed.isEmpty())
        assertSame(pieces, gaveUp.single())
        assertFalse(run.busy.value)
        assertFalse(run.repeating.value)
    }

    @Test
    fun `the level a press asks for reaches the completer`() = runTest {
        val run = run()
        run.press(pieces, landed::add, Difficulty.Level.Hard)
        runCurrent()
        run.press(landed.last(), landed::add, Difficulty.Level.Easy)
        runCurrent()

        assertEquals(listOf<Difficulty.Level?>(Difficulty.Level.Hard, Difficulty.Level.Easy), editor.targets)
    }

    @Test
    fun `Hard and Expert may take four seconds, Easy and Medium two`() = runTest {
        var polls = 0
        editor.completionOf = { _, _, budget ->
            while (!budget.exhausted()) polls++
            Completion.GaveUp
        }
        var clock = 0L
        // Every read of the clock is one second, so a 2 s budget allows one poll and a 4 s budget three.
        val run = run(nanoTime = { clock += ONE_SECOND_NANOS; clock })

        val allowed = Difficulty.Level.entries.associateWith { level ->
            polls = 0
            run.press(pieces, landed::add, level)
            runCurrent()
            polls
        }

        assertEquals(
            mapOf(Difficulty.Level.Easy to 1, Difficulty.Level.Medium to 1, Difficulty.Level.Hard to 3, Difficulty.Level.Expert to 3),
            allowed,
        )
    }

    @Test
    fun `a completer that gives up raises the message and lands nothing`() = runTest {
        editor.completionOf = { _, _, _ -> Completion.GaveUp }
        val run = run()
        run.press(pieces, landed::add)
        runCurrent()

        assertTrue(landed.isEmpty())
        assertSame(pieces, gaveUp.single())
        assertFalse(run.busy.value)
    }

    @Test
    fun `a repeat that gives up keeps the first completion and the chain`() = runTest {
        val run = run()
        run.press(pieces, landed::add)
        runCurrent()
        editor.completionOf = { _, _, _ -> Completion.GaveUp }

        run.press(landed.single(), landed::add)
        runCurrent()

        assertEquals(1, landed.size)
        assertSame("the give-up is about the author's pieces, not the completion on the board", pieces, gaveUp.single())
        assertTrue("the board is still an unedited completion", run.repeating.value)
    }

    @Test
    fun `an edit ends the chain, so the next press completes the board as it stands`() = runTest {
        val run = run()
        run.press(pieces, landed::add)
        runCurrent()
        val edited = editor.draft(marks = 9)

        run.boardChanged(edited)
        assertFalse(run.repeating.value)
        run.press(edited, landed::add)
        runCurrent()

        assertEquals(listOf(TestDraft(2), TestDraft(9)), editor.completed)
    }

    @Test
    fun `the board the run just landed does not end its own chain`() = runTest {
        val run = run()
        run.press(pieces, { next ->
            run.boardChanged(next)
            landed += next
        })
        runCurrent()

        assertTrue(run.repeating.value)
    }

    @Test
    fun `a second tap while it runs cancels it silently`() = runTest {
        val blocked = BlockingCompleter()
        editor.completionOf = blocked
        val run = run(default = Dispatchers.Default, budgetMs = { LONG_BUDGET_MS })

        run.press(pieces, landed::add)
        runCurrent()
        assertTrue(blocked.started.await(AWAIT_S, TimeUnit.SECONDS))
        assertTrue(run.busy.value)

        run.press(pieces, landed::add)

        assertFalse("not busy once cancelled", run.busy.value)
        assertTrue("the completer's budget saw the cancel", blocked.stopped.await(AWAIT_S, TimeUnit.SECONDS))
        settle()
        assertTrue(landed.isEmpty())
        assertTrue("a cancel is silent", gaveUp.isEmpty())
    }

    @Test
    fun `an edit while it runs cancels it silently`() = runTest {
        val blocked = BlockingCompleter()
        editor.completionOf = blocked
        val run = run(default = Dispatchers.Default, budgetMs = { LONG_BUDGET_MS })

        run.press(pieces, landed::add)
        runCurrent()
        assertTrue(blocked.started.await(AWAIT_S, TimeUnit.SECONDS))

        run.boardChanged(editor.draft(marks = 3))

        assertFalse(run.busy.value)
        assertTrue(blocked.stopped.await(AWAIT_S, TimeUnit.SECONDS))
        settle()
        assertTrue(landed.isEmpty())
        assertTrue(gaveUp.isEmpty())
    }

    @Test
    fun `leaving the Studio cancels it silently`() = runTest {
        val blocked = BlockingCompleter()
        editor.completionOf = blocked
        val studio = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val run = run(default = Dispatchers.Default, scope = studio, budgetMs = { LONG_BUDGET_MS })

        run.press(pieces, landed::add)
        runCurrent()
        assertTrue(blocked.started.await(AWAIT_S, TimeUnit.SECONDS))

        studio.cancel()

        assertTrue(blocked.stopped.await(AWAIT_S, TimeUnit.SECONDS))
        settle()
        assertTrue(landed.isEmpty())
        assertTrue(gaveUp.isEmpty())
    }

    @Test
    fun `a press after a cancelled run still lands, and the cancelled run's end does not undo it`() = runTest {
        val blocked = BlockingCompleter()
        editor.completionOf = blocked
        val run = run(default = Dispatchers.Default, budgetMs = { LONG_BUDGET_MS })
        run.press(pieces, landed::add)
        runCurrent()
        assertTrue(blocked.started.await(AWAIT_S, TimeUnit.SECONDS))
        run.press(pieces, landed::add)
        assertTrue(blocked.stopped.await(AWAIT_S, TimeUnit.SECONDS))
        editor.completionOf = { draft, random, _ -> Completion.Done(TestDraft(draft.marks + random.nextInt(1, 10))) }

        run.press(pieces, landed::add)
        while (landed.isEmpty()) {
            Thread.sleep(POLL_MS)
            runCurrent()
        }

        assertEquals(1, landed.size)
        assertTrue(run.repeating.value)
        assertFalse(run.busy.value)
    }

    /** Lets a cancelled run's thread hand back to the test scheduler. */
    private fun TestScope.settle() {
        repeat(SETTLE_ROUNDS) {
            Thread.sleep(POLL_MS)
            advanceUntilIdle()
        }
    }

    /** A completer that works until its budget says stop; [stopped] counts down once it sees it. */
    private class BlockingCompleter : (TestDraft, Random, Budget) -> Completion<TestDraft> {
        val started = CountDownLatch(1)
        val stopped = CountDownLatch(1)

        override fun invoke(draft: TestDraft, random: Random, budget: Budget): Completion<TestDraft> {
            started.countDown()
            while (!budget.exhausted()) Thread.onSpinWait()
            stopped.countDown()
            return Completion.GaveUp
        }
    }

    private companion object {
        const val SEED = 7
        const val ONE_SECOND_NANOS = 1_000_000_000L
        const val LONG_BUDGET_MS = 60_000L
        const val AWAIT_S = 3L
        const val POLL_MS = 10L
        const val SETTLE_ROUNDS = 5
    }
}
