package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DraftAutosave] (spec A5): a trailing throttle, not a debounce, so a steady run of edits is
 * still stored every window; writes are conflated and never overlap; finish stores the last
 * row after any write already running and takes no more.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DraftAutosaveTest {

    private val stored = mutableListOf<String>()

    private fun TestScope.autosave(store: suspend (Draft) -> Unit = { stored += it.title }) =
        DraftAutosave(backgroundScope, store)

    @Test
    fun `edits every 100 ms are still stored 300 ms after the first`() = runTest {
        val autosave = autosave()
        repeat(5) { i ->
            autosave.offer(row("edit $i"))
            advanceTimeBy(100)
        }
        // Offers at 0, 100, 200, 300, 400 ms: the window opened at 0 ends at 300 with the newest
        // (offered at 300); a debounce would still be waiting for the edits to stop.
        assertEquals(listOf("edit 3"), stored)

        advanceTimeBy(WINDOW_MS)
        assertEquals(listOf("edit 3", "edit 4"), stored)
        assertFalse(autosave.saving.value)
    }

    @Test
    fun `flush stores now and closes the window`() = runTest {
        val autosave = autosave()
        autosave.offer(row("edit"))
        assertTrue(autosave.saving.value)

        autosave.flush()
        assertEquals(listOf("edit"), stored)
        assertFalse(autosave.saving.value)

        advanceTimeBy(WINDOW_MS * 2)
        assertEquals(listOf("edit"), stored)
    }

    @Test
    fun `finish stores the pending row and later offers are never stored`() = runTest {
        val autosave = autosave()
        autosave.offer(row("edit"))

        autosave.finish()
        assertEquals(listOf("edit"), stored)
        autosave.offer(row("late"))
        advanceTimeBy(WINDOW_MS * 2)

        assertEquals(listOf("edit"), stored)
        assertFalse(autosave.saving.value)
    }

    @Test
    fun `finish waits for a write already running, then stores the newer row`() = runTest {
        val release = CompletableDeferred<Unit>()
        val autosave = autosave { stored += it.title; release.await() }
        autosave.offer(row("first"))
        advanceTimeBy(WINDOW_MS + 1)
        autosave.offer(row("second"))

        var finished = false
        backgroundScope.launch {
            autosave.finish()
            finished = true
        }
        runCurrent()
        assertFalse("still waiting for the running write", finished)
        assertEquals(listOf("first"), stored)

        release.complete(Unit)
        runCurrent()
        assertTrue(finished)
        assertEquals(listOf("first", "second"), stored)
    }

    private fun row(title: String) = Draft(
        id = "draft-1",
        gameId = GameId("queens"),
        variant = VariantId("7"),
        draftFormatVersion = 1,
        blob = byteArrayOf(),
        title = title,
        updatedAt = Instant.EPOCH,
        testSolve = null,
        reservedCode = null,
        imported = false,
        authorName = "",
        note = "",
    )

    private companion object {
        const val WINDOW_MS = 300L
    }
}
