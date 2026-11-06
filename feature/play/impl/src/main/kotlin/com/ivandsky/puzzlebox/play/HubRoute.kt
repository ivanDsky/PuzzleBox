package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * The Play tab's root (A5, A13, N2): the games hub shows the [PlaySessionStore]'s playable
 * games in registry order as cards, each with a live thumbnail of today's board (drawn by
 * the game's own [GamePlayRegistry] entry), today's progress and best time
 * ([HubViewModel], SC11), stacked or, in [PlayLayout.TwoPane], in a grid. Tapping a card
 * is [onPick]; the entry pushes that game's key, so back from the board returns here (N3)
 * and an in-progress game resumes its save. A game with an archive also opens a past day
 * by date, [onPickDay] (A22). "Older puzzles" is [onOpenHistory] (N3). The hub never
 * opens a play; each start prefetches today's uncached puzzles ([HubViewModel.syncToday], A12).
 */
@Composable
internal fun HubRoute(
    layout: PlayLayout,
    onPick: (GameId) -> Unit,
    onPickDay: (GameId, LocalDate) -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    games: GamePlayRegistry = koinInject(),
) {
    val viewModel: HubViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.syncToday()
        onStopOrDispose { }
    }
    GamePicker(
        state = state,
        games = games,
        layout = layout,
        onPick = onPick,
        onPickDay = onPickDay,
        onOpenHistory = onOpenHistory,
        modifier = modifier,
    )
}
