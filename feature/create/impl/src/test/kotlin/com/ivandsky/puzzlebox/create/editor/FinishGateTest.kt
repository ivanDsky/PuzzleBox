package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.create.FakeDraftCompleter
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What lets a press through the view model's Finish holder: the flag, an open editable board and the enabling rule. */
class FinishGateTest {

    private val editor = FakeDraftCompleter()
    private val pieces = editor.draft(marks = 2)
    private val landed = mutableListOf<EditorDraft<*>>()
    private var locked = false
    private var verdict: Verdict = Verdict.Unique(Solution(listOf(2)))

    private fun TestScope.finish(): EditorViewModelFinish {
        val dispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler)
        val lanes = object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Main
            override val io: CoroutineDispatcher = dispatcher
            override val default: CoroutineDispatcher = dispatcher
        }
        return EditorViewModelFinish(
            scope = CoroutineScope(dispatcher + Job()),
            dispatchers = lanes,
            random = Random(1),
            choice = FinishLevelChoice(),
            board = { pieces },
            verdict = { verdict },
            settled = MutableStateFlow(Verdict.Checking),
            locked = { locked },
            land = { landed += it },
        )
    }

    @Test
    fun `with an editable settled board a press lands a completion`() = runTest {
        val finish = finish()

        finish.press()
        runCurrent()

        assertEquals(1, landed.size)
    }

    @Test
    fun `a published draft takes no Finish`() = runTest {
        val finish = finish()
        locked = true

        finish.press()
        runCurrent()

        assertTrue(landed.isEmpty())
        assertFalse(finish.busy.value)
    }

    @Test
    fun `a press while the verdict is Checking or None does nothing`() = runTest {
        val finish = finish()

        verdict = Verdict.Checking
        finish.press()
        runCurrent()
        verdict = Verdict.None(setOf(0))
        finish.press()
        runCurrent()

        assertTrue(landed.isEmpty())
        assertTrue(editor.completed.isEmpty())
    }
}
