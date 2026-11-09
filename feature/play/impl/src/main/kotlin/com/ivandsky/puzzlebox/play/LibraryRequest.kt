package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.model.GameId

/**
 * A one-shot ask from outside Games, held by the app shell and taken once: open a game's
 * [GamePage], or show a [Section].
 */
sealed interface LibraryRequest {
    /** Open [gameId]'s page: over the phone's list on the Dailies tab, or its row selected on the tablet. */
    data class GamePage(val gameId: String) : LibraryRequest

    /** Show [tab], closing any open page. */
    data class Section(val tab: GamesTab) : LibraryRequest
}

/**
 * Acts on [request] once, then reports it taken ([onTaken]): a [LibraryRequest.GamePage] for
 * a game in [rows] calls [onGamePage], and one for any other game is dropped (acting on it
 * would show the first row instead); a [LibraryRequest.Section] calls [onSection]. The same
 * request composed again, or restored after a recreation, does not act twice; a new one does.
 */
@Composable
internal fun ConsumeLibraryRequest(
    request: LibraryRequest?,
    rows: List<GameId>,
    onGamePage: (GameId) -> Unit,
    onSection: (GamesTab) -> Unit,
    onTaken: () -> Unit,
) {
    var acted by rememberSaveable(request) { mutableStateOf(false) }
    LaunchedEffect(request) {
        if (request == null || acted) return@LaunchedEffect
        when (request) {
            is LibraryRequest.GamePage -> GameId(request.gameId).takeIf { it in rows }?.let(onGamePage)
            is LibraryRequest.Section -> onSection(request.tab)
        }
        acted = true
        onTaken()
    }
}
