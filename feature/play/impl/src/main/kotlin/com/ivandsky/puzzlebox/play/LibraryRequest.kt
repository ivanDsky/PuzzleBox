package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * A one-shot ask from outside Games, held by the app shell and taken once: show a [Section].
 */
sealed interface LibraryRequest {
    /**
     * Show [tab], closing any open page. Friends and Custom name the same place: whichever of
     * the two the pills have is the one shown ([shownWith]).
     */
    data class Section(val tab: GamesTab) : LibraryRequest
}

/**
 * Acts on [request] once, then reports it taken ([onTaken]): a [LibraryRequest.Section] calls [onSection]. The same
 * request composed again, or restored after a recreation, does not act twice; a new one does.
 */
@Composable
internal fun ConsumeLibraryRequest(
    request: LibraryRequest?,
    onSection: suspend (GamesTab) -> Unit,
    onTaken: () -> Unit,
) {
    var acted by rememberSaveable(request) { mutableStateOf(false) }
    LaunchedEffect(request) {
        if (request == null || acted) return@LaunchedEffect
        when (request) {
            is LibraryRequest.Section -> onSection(request.tab)
        }
        acted = true
        onTaken()
    }
}
