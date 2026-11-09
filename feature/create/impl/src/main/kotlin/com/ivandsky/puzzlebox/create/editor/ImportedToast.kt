package com.ivandsky.puzzlebox.create.editor

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.flow.filter

/**
 * "Imported as a new draft." once, when [pending] turns true on the Studio the import opened.
 * It reports [onShown] as it starts, so the notice is spent at once: a rotation or a return to
 * this Studio never shows it again (it may cut the one showing short, never repeat it). The
 * showing toast stays up when [pending] drops.
 */
@Composable
internal fun ImportedToast(pending: Boolean, onShown: () -> Unit, modifier: Modifier) {
    val snackbar = remember { SnackbarHostState() }
    val shown by rememberUpdatedState(onShown)
    val wanted by rememberUpdatedState(pending)
    LaunchedEffect(snackbar) {
        snapshotFlow { wanted }.filter { it }.collect {
            shown()
            snackbar.showSnackbar("Imported as a new draft.")
        }
    }
    SnackbarHost(snackbar, modifier) { data ->
        Snackbar(data, Modifier.testTag("create.toast.imported"))
    }
}
