package com.ivandsky.puzzlebox.create.editor

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter

/** What the Studio says when Finish gives up (E1), word for word. */
internal const val FINISH_GAVE_UP_MESSAGE = "Too many unknowns to finish this draft. Place a few more and try again."

/**
 * A give-up waiting to be shown: [raise] puts it up, and the toast [consume]s it as it starts, so
 * a rotation or a return to the Studio never shows the same failure twice.
 */
internal class FinishGaveUpToastState {
    private val waiting = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = waiting.asStateFlow()

    fun raise() {
        waiting.value = true
    }

    fun consume() {
        waiting.value = false
    }
}

/** The [FINISH_GAVE_UP_MESSAGE] snackbar, once per give-up [state] raises. */
@Composable
internal fun FinishGaveUpToast(state: FinishGaveUpToastState, modifier: Modifier) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(snackbar, state) {
        state.pending.filter { it }.collect {
            state.consume()
            snackbar.showSnackbar(FINISH_GAVE_UP_MESSAGE)
        }
    }
    SnackbarHost(snackbar, modifier) { data ->
        Snackbar(data, Modifier.testTag("create.toast.finishGaveUp"))
    }
}
