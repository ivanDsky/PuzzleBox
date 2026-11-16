package com.ivandsky.puzzlebox.play.friend

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.withTimeoutOrNull

/** How long "Reported" stays up before the screen leaves, in milliseconds. */
internal const val REPORTED_NOTICE_MS = 2_000L

internal const val REPORTED_MESSAGE = "Reported. It's hidden on this device."

/** Shows [notice] once, then says it is [onShown]. */
@Composable
internal fun FriendNoticeSnackbar(notice: FriendNotice?, onShown: (Int) -> Unit, modifier: Modifier = Modifier) {
    val host = remember { SnackbarHostState() }
    LaunchedEffect(notice) {
        if (notice == null) return@LaunchedEffect
        host.currentSnackbarData?.dismiss()
        host.showSnackbar(notice.message)
        onShown(notice.id)
    }
    SnackbarHost(host, modifier) { data -> Snackbar(data, Modifier.testTag("friend.notice")) }
}

/** The reported card's snackbar: up for [REPORTED_NOTICE_MS], then the screen leaves through [onDone]. */
@Composable
internal fun FriendReportedNotice(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val host = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        withTimeoutOrNull(REPORTED_NOTICE_MS) { host.showSnackbar(REPORTED_MESSAGE, duration = SnackbarDuration.Indefinite) }
        onDone()
    }
    SnackbarHost(host, modifier) { data -> Snackbar(data, Modifier.testTag("friend.notice.reported")) }
}
