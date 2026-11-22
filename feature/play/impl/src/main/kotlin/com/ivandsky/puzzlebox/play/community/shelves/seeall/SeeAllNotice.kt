package com.ivandsky.puzzlebox.play.community.shelves.seeall

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.play.community.CommunityNotice

/** Shows [notice] once, then says it is [onShown]. */
@Composable
internal fun SeeAllNotice(notice: CommunityNotice?, onShown: (Int) -> Unit, modifier: Modifier = Modifier) {
    val host = remember { SnackbarHostState() }
    LaunchedEffect(notice) {
        if (notice == null) return@LaunchedEffect
        host.currentSnackbarData?.dismiss()
        host.showSnackbar(notice.message)
        onShown(notice.id)
    }
    SnackbarHost(host, modifier) { data -> Snackbar(data, Modifier.testTag("seeall.notice")) }
}
