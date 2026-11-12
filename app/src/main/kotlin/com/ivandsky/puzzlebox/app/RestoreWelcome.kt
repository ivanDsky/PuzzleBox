package com.ivandsky.puzzlebox.app

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.data.cloud.RestoreSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** A33's message for one first restore. */
internal fun restoreWelcomeText(summary: RestoreSummary): String =
    "Welcome back — ${summary.games} games, ${summary.drafts} drafts, ${summary.boardDays} board days restored"

/**
 * The one-time "Welcome back" message (A33): every summary [restored] emits shows one message, queued behind one
 * still showing, and never again, since the engine emits a summary once per account per device. [restored] is null
 * while cloud sync is not bound, and then nothing is shown. A rotation may cut the message showing short; it never
 * repeats it.
 */
@Composable
internal fun RestoreWelcome(restored: Flow<RestoreSummary>?, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(restored) {
        // One launch per summary: showSnackbar suspends until its message is gone, and the host queues them.
        restored?.collect { summary -> launch { snackbar.showSnackbar(restoreWelcomeText(summary)) } }
    }
    SnackbarHost(snackbar, modifier) { data ->
        Snackbar(data, Modifier.testTag("shell.restore.welcome"))
    }
}
