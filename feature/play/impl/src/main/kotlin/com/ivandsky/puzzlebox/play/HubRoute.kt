package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import org.koin.androidx.compose.koinViewModel

/**
 * The Play tab's root (A5, A13, N2): the games hub shows the [PlaySessionStore]'s playable
 * games in registry order as brand cards, each with today's progress and best time
 * ([HubViewModel], SC11), stacked or, in [PlayLayout.TwoPane], in a grid. Picking one is
 * [onPick]; the entry pushes that game's key, so back from the board returns here (N3)
 * and an in-progress game resumes its save. "Older puzzles" is [onOpenHistory] (N3). The
 * hub never opens a play; each start prefetches today's uncached puzzles
 * ([HubViewModel.syncToday], A12).
 */
@Composable
internal fun HubRoute(
    layout: PlayLayout,
    onPick: (GameId) -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HubViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.syncToday()
        onStopOrDispose { }
    }
    GamePicker(
        state = state,
        layout = layout,
        onPick = onPick,
        onOpenHistory = onOpenHistory,
        modifier = modifier,
    )
}
