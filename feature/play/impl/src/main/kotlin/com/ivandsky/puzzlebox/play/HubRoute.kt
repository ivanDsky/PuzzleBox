package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.model.GameId
import org.koin.androidx.compose.koinViewModel

/**
 * The Play tab's root (A5, N2): the games hub lists the [PlaySessionStore]'s playable
 * games in registry order, each row marked with today's progress ([HubViewModel], A13,
 * SC11). Picking one is [onPick]; the entry pushes that game's key, so back from the board
 * returns here (N3) and an in-progress game resumes its save. The hub never opens a play;
 * each start prefetches today's uncached puzzles ([HubViewModel.syncToday], A12).
 */
@Composable
internal fun HubRoute(onPick: (GameId) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: HubViewModel = koinViewModel()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.syncToday()
        onStopOrDispose { }
    }
    GamePicker(rows = rows, onPick = onPick, modifier = modifier)
}
