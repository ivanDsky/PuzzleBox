package com.ivandsky.puzzlebox.compete.duel

import com.ivandsky.puzzlebox.domain.DeleteSelfDuel
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
 * [deleteSelfDuel] and then calls [onRemoved]. A delete that fails (it is not transactional, so the duel may be left part-deleted and listed) puts the confirm back to Idle and does not call [onRemoved]; the user can remove again. A null [code] (a link
 * that reads as no duel) has nothing to remove.
 */
internal class SelfDuelRemoval(
    private val code: ShareCode?,
    private val deleteSelfDuel: DeleteSelfDuel,
    private val scope: CoroutineScope,
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
            } catch (_: Exception) {
                mutableStep.value = RemovalStep.Idle
                return@launch
            }
            onRemoved()
        }
    }
}
