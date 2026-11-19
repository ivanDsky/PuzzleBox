package com.ivandsky.puzzlebox.compete.duel

import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.common.log.LogcatFailureLog
import com.ivandsky.puzzlebox.compete.domain.DeleteSelfDuel
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where removing a self duel stands: not asked, waiting on Remove / Keep, or the delete under way. */
internal enum class RemovalStep { Idle, Asking, Removing }

/**
 * The remove-confirm of one self duel: [ask] opens it, [keep] closes it, [remove] deletes the duel through
 * [deleteSelfDuel] and then calls [onRemoved]. A delete that fails is reported to [failures]; it may leave the duel
 * part-deleted and listed (the delete is not transactional), so the confirm goes back to Idle, [onRemoved] is not
 * called, and the user can remove again. A null [code] (a link that reads as no duel) has nothing to remove.
 */
internal class SelfDuelRemoval(
    private val code: ShareCode?,
    private val deleteSelfDuel: DeleteSelfDuel,
    private val scope: CoroutineScope,
    private val failures: FailureLog = LogcatFailureLog,
    private val onRemoved: suspend () -> Unit,
) {
    private val mutableStep = MutableStateFlow(RemovalStep.Idle)
    val step: StateFlow<RemovalStep> = mutableStep.asStateFlow()

    fun ask() {
        mutableStep.compareAndSet(RemovalStep.Idle, RemovalStep.Asking)
    }

    fun keep() {
        mutableStep.compareAndSet(RemovalStep.Asking, RemovalStep.Idle)
    }

    fun remove() {
        val target = code ?: return
        if (!mutableStep.compareAndSet(RemovalStep.Asking, RemovalStep.Removing)) return
        scope.launch {
            try {
                deleteSelfDuel(target)
            } catch (e: CancellationException) {
                mutableStep.value = RemovalStep.Idle
                throw e
            } catch (e: Exception) {
                failures.warn(TAG, "self duel delete failed", e)
                mutableStep.value = RemovalStep.Idle
                return@launch
            }
            onRemoved()
        }
    }
}

private const val TAG = "SelfDuelRemoval"
