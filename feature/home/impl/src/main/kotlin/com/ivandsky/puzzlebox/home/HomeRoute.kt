package com.ivandsky.puzzlebox.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.model.GameId
import org.koin.androidx.compose.koinViewModel

/**
 * Home's DI-bound entry point: renders [HomeViewModel]'s state. The pane layout is
 * decided once at the app shell (width class + orientation) and passed in; UI below
 * this point stays repository-free. [onOpenGame] reports the [GameId] a Today tile or the
 * CTA opens (the CTA's is [HomeUiState.playTarget]: the saved game, else the next unsolved
 * one). Every resume prefetches today's uncached puzzles (A12), so the tiles can say which
 * ones are offline, and checks for a newer release, whose pill opens it in the browser.
 */
@Composable
fun HomeRoute(
    layout: HomeLayout,
    onOpenGame: (GameId) -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenHistory: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    LifecycleResumeEffect(viewModel) {
        viewModel.syncToday()
        viewModel.checkForUpdate()
        onPauseOrDispose { }
    }

    HomeScreen(
        layout = layout,
        state = uiState,
        onOpenGame = onOpenGame,
        onOpenProfile = onOpenProfile,
        onOpenHistory = onOpenHistory,
        onOpenUpdate = { url ->
            // AndroidUriHandler throws this when no app can open the link; the tap then does nothing (A6).
            try {
                uriHandler.openUri(url)
            } catch (_: IllegalArgumentException) {
            }
        },
        modifier = modifier.testTag("route.home"),
    )
}
