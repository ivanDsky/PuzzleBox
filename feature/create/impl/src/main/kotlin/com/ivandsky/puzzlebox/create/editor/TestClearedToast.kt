package com.ivandsky.puzzlebox.create.editor

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * The silent clear's toast: "Board changed — test solve cleared." with Undo ([onUndo]), shown
 * while [shown]. A timeout or swipe reports [onGone]; [shown] dropping (the next board change)
 * removes it at once, so its Undo never acts on a newer edit.
 */
@Composable
internal fun TestClearedToast(shown: Boolean, onUndo: () -> Unit, onGone: () -> Unit, modifier: Modifier) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(shown) {
        if (!shown) return@LaunchedEffect
        val result = snackbar.showSnackbar("Board changed — test solve cleared.", actionLabel = "Undo", duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) onUndo() else onGone()
    }
    SnackbarHost(snackbar, modifier) { data ->
        Snackbar(data, Modifier.testTag("create.toast.testCleared"))
    }
}
