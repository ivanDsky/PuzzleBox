package com.ivandsky.puzzlebox.create

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Undo names ≤ 28 title chars. */
private const val DELETED_TITLE_MAX = 28

/**
 * The delete-Undo snackbar of both Create roots (the Create tab and the Custom list). While [deleted] is set it says
 * "Deleted “{title}”" and offers Undo ([onUndo]); it leaves when the state clears [deleted] (the 5 s window is the
 * ViewModel's). The returned state goes to [DeleteUndoSnackbar], and tells a root whether the snackbar is up.
 */
@Composable
internal fun rememberDeleteUndo(deleted: CreateUiState.Deleted?, onUndo: () -> Unit): SnackbarHostState {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(deleted) {
        val gone = deleted ?: return@LaunchedEffect
        val title = gone.title.let { if (it.length > DELETED_TITLE_MAX) it.take(DELETED_TITLE_MAX) + "…" else it }
        val result = snackbar.showSnackbar("Deleted “$title”", actionLabel = "Undo", duration = SnackbarDuration.Indefinite)
        if (result == SnackbarResult.ActionPerformed) onUndo()
    }
    return snackbar
}

/** Where [rememberDeleteUndo]'s snackbar shows; the root places it with [modifier]. */
@Composable
internal fun DeleteUndoSnackbar(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier.testTag("create.snackbar"))
}
