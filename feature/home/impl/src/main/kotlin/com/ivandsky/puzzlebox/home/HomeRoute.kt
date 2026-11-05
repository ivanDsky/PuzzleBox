package com.ivandsky.puzzlebox.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ThemeMode
import org.koin.androidx.compose.koinViewModel

/**
 * Home's DI-bound entry point: renders [HomeViewModel]'s state. The pane layout is
 * decided once at the app shell (width class + orientation) and passed in; UI below
 * this point stays repository-free. [onPlayToday] reports the featured game's [GameId].
 */
@Composable
fun HomeRoute(
    layout: HomeLayout,
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
    onPlayToday: (GameId) -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenHistory: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreen(
        layout = layout,
        game = uiState.featured,
        identity = uiState.identity,
        themeMode = themeMode,
        onSetThemeMode = onSetThemeMode,
        onOpenHistory = onOpenHistory,
        history = uiState.history,
        onPlayToday = { onPlayToday(uiState.featured.id) },
        onOpenProfile = onOpenProfile,
        modifier = modifier,
    )
}
