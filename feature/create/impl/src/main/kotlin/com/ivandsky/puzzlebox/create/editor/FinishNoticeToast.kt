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
import kotlinx.coroutines.flow.filterNotNull

/**
 * A [FinishNotice] waiting to be shown: [raise] puts it up, and the toast [consume]s it as it
 * starts, so a rotation or a return to the Studio never shows the same one twice.
 */
internal class FinishNoticeState {
    private val waiting = MutableStateFlow<FinishNotice?>(null)
    val pending: StateFlow<FinishNotice?> = waiting.asStateFlow()

    fun raise(notice: FinishNotice) {
        waiting.value = notice
    }

    fun consume() {
        waiting.value = null
    }
}

/** The snackbar of a notice's [FinishNotice.text], once per notice [state] raises. */
@Composable
internal fun FinishNoticeToast(state: FinishNoticeState, modifier: Modifier) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(snackbar, state) {
        state.pending.filterNotNull().collect { notice ->
            state.consume()
            snackbar.showSnackbar(notice.text)
        }
    }
    SnackbarHost(snackbar, modifier) { data ->
        Snackbar(data, Modifier.testTag("create.toast.finishGaveUp"))
    }
}
