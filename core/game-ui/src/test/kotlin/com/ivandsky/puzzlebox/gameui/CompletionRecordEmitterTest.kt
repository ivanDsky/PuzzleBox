package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.common.result.AppError
import com.ivandsky.puzzlebox.common.result.AppResult
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private data class FakeRef(
    val id: String,
    override val gameId: com.ivandsky.puzzlebox.model.GameId = com.ivandsky.puzzlebox.model.GameId(id),
    override val key: com.ivandsky.puzzlebox.model.PuzzleKey =
        com.ivandsky.puzzlebox.model.PuzzleKey.Daily(java.time.LocalDate.of(2026, 11, 2), null),
) : PuzzleRef

private fun record(id: String) = CompletionRecord(
    puzzleRef = FakeRef(id),
    time = 12_345L,
    hints = 2,
    stats = RecordStats(1, "{\"v\":1}"),
)

/** F2 §7: emission is fire-and-observe, deduped per puzzle, never blocks the overlay. */
class CompletionRecordEmitterTest {

    @Test
    fun `emits once per puzzle ref and reports the write result`() = runTest {
        val written = CompletableDeferred<CompletionRecord>()
        var second: CompletableDeferred<CompletionRecord>? = null
        val emitter = CompletionRecordEmitter(backgroundScope) { record ->
            synchronized(written) {
                if (!written.isCompleted) written.complete(record)
                else (second ?: CompletableDeferred<CompletionRecord>().also { second = it }).complete(record)
            }
            AppResult.Ok(Unit)
        }

        val first = emitter.emit(record("zip-592"))
        val duplicate = emitter.emit(record("zip-592"))
        val otherPuzzle = emitter.emit(record("zip-593"))
        advanceUntilIdle()

        assertTrue(first)
        assertFalse(duplicate) // recomposition race deduped, nothing written twice
        assertTrue(otherPuzzle)
        assertEquals("zip-592", (written.await().puzzleRef as FakeRef).id)
        assertEquals("zip-593", (second?.await()?.puzzleRef as FakeRef).id)
    }

    @Test
    fun `write failures surface through the callback instead of throwing`() = runTest {
        val observed = CompletableDeferred<AppResult<Unit>>()
        val gate = CompletableDeferred<Unit>()
        val emitter = CompletionRecordEmitter(backgroundScope) { _ ->
            gate.await()
            AppResult.Err(AppError.Unknown(IllegalStateException("disk full")))
        }

        val started = emitter.emit(record("zip-592")) { observed.complete(it) }
        assertTrue(started)
        assertTrue(!observed.isCompleted) // nothing blocks: overlay already celebrating

        gate.complete(Unit)
        val result = observed.await()

        assertTrue(result is AppResult.Err)
        assertTrue((result as AppResult.Err).error is AppError.Unknown)
    }

    @Test
    fun `throwing writers convert to typed unknown errors`() = runTest {
        val observed = CompletableDeferred<AppResult<Unit>>()
        val emitter = CompletionRecordEmitter(backgroundScope) { _ ->
            throw IllegalArgumentException("bad record")
        }

        emitter.emit(record("zip-592")) { observed.complete(it) }

        assertTrue(observed.await() is AppResult.Err)
    }
}
