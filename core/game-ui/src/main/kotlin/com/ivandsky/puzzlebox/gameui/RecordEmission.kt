package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.common.result.AppResult
import com.ivandsky.puzzlebox.game.CompletionRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The completion-record WRITE FLOW (F1 §3; A4 first-win semantics are stamped core/data
 * side — this only emits). Fire-and-observe: the write launches on [scope] and its result
 * surfaces through [onResult], never by blocking the win overlay (F2 §7). One record per
 * [CompletionRecord.puzzleRef] per emitter instance — recomposition races cannot
 * double-write; a replay (new session) is a new emitter lifetime and emits again.
 *
 * Single-threaded by contract: [emit] must be called from the main UI thread only (the
 * dedup set is a plain [HashSet], not thread-safe), matching the win-overlay call site.
 */
class CompletionRecordEmitter(
    private val scope: CoroutineScope,
    private val write: suspend (CompletionRecord) -> AppResult<Unit>,
) {
    private val emitted = HashSet<Any>()

    /** @return true if this record started (or completed) a write; false if deduped. */
    fun emit(record: CompletionRecord, onResult: (AppResult<Unit>) -> Unit = {}): Boolean {
        if (!emitted.add(record.puzzleRef)) return false
        scope.launch {
            val result = try {
                write(record)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                AppResult.Err(com.ivandsky.puzzlebox.common.result.AppError.Unknown(t))
            }
            onResult(result)
        }
        return true
    }
}
