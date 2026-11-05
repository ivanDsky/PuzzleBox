package com.ivandsky.puzzlebox.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ThemeMode
import org.koin.androidx.compose.koinViewModel

/**
 * Home's DI-bound entry point: renders [HomeViewModel]'s state. The pane layout is
 * decided once at the app shell (width class + orientation) and passed in; UI below
 * this point stays repository-free. [onOpenGame] reports the [GameId] a Today tile or the
 * CTA opens (the CTA's is [HomeUiState.playTarget]: the saved game, else the next unsolved
 * one). Every resume prefetches today's uncached puzzles (A12), so the tiles can say which
 * ones are offline.
 */
@Composable
fun HomeRoute(
    layout: HomeLayout,
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
    onOpenGame: (GameId) -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenHistory: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.syncToday()
        onPauseOrDispose { }
    }

    HomeScreen(
        layout = layout,
        state = uiState,
        themeMode = themeMode,
        onSetThemeMode = onSetThemeMode,
        onOpenGame = onOpenGame,
        onOpenProfile = onOpenProfile,
        onOpenHistory = onOpenHistory,
        modifier = modifier.testTag("route.home"),
    )
}
