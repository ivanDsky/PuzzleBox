package com.ivandsky.puzzlebox.create.published

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.model.ShareCode

/** "Unpublished — K7Q-2MX no longer opens for new people" each time [done] grows. */
@Composable
internal fun UnpublishedToast(code: ShareCode?, done: Int, modifier: Modifier) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(done) {
        if (done == 0 || code == null) return@LaunchedEffect
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar("Unpublished — ${code.display} no longer opens for new people")
    }
    SnackbarHost(snackbar, modifier) { data ->
        Snackbar(data, Modifier.testTag("create.toast.unpublished"))
    }
}
