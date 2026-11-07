package com.ivandsky.puzzlebox.create.published

import com.ivandsky.puzzlebox.data.repository.UnpublishResult
import com.ivandsky.puzzlebox.domain.UpdatePublishState
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Unpublish behind its confirm dialog: [ask] opens it, [cancel] closes it changing nothing, and
 * [confirm] takes the code down through [updatePublishState]. Done closes the dialog and counts
 * a [State.done] for the toast; offline or refused, the dialog stays open with [State.failure],
 * nothing changed, so Unpublish can be pressed again. One call at a time. Confined to the main
 * thread, like the screens' other holders.
 */
internal class UnpublishSession(private val updatePublishState: UpdatePublishState, private val scope: CoroutineScope) {

    /** [asking] while the dialog is open, [busy] while the call runs, [done] unpublishes so far. */
    data class State(
        val asking: Boolean = false,
        val busy: Boolean = false,
        val failure: Failure? = null,
        val done: Int = 0,
    )

    /** Why the last confirm changed nothing. */
    enum class Failure { Offline, Rejected }

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    fun ask() {
        mutableState.update { it.copy(asking = true, failure = null) }
    }

    /** Closes the dialog; ignored while the call runs, whose answer must land. */
    fun cancel() {
        mutableState.update { if (it.busy) it else it.copy(asking = false, failure = null) }
    }

    fun confirm(code: ShareCode) {
        val now = mutableState.value
        if (!now.asking || now.busy) return
        mutableState.update { it.copy(busy = true, failure = null) }
        scope.launch {
            val result = updatePublishState(code)
            mutableState.update {
                when (result) {
                    UnpublishResult.Done -> State(done = it.done + 1)
                    UnpublishResult.Offline -> it.copy(busy = false, failure = Failure.Offline)
                    is UnpublishResult.Rejected -> it.copy(busy = false, failure = Failure.Rejected)
                }
            }
        }
    }
}
