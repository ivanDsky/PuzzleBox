package com.ivandsky.puzzlebox.play.random

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.domain.random.RandomPick
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Pick a random puzzle: [game] is the preset chip (null is Any), [games] the Game chips of Past puzzles and
 * [communityGames] those of New today (see [communityGamesOf]). Switching source keeps the chosen game when the new source
 * offers it, else falls back to Any. Once a pick opened and
 * the sheet hid, [onOpen] gets it and the sheet goes back to idle; an outcome that opened nothing says why in the sheet
 * and leaves the filters as they were. A swipe or the scrim cancels a pick in flight, then [onDismiss] runs.
 *
 * Motion: the sheet's **Slide in / Slide out** and the scrim's **Fade** are ModalBottomSheet's own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RandomPuzzleSheet(
    game: GameId?,
    games: List<RandomGame>,
    communityGames: List<RandomGame>,
    layout: PlayLayout,
    onOpen: (RandomPick) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RandomPuzzleViewModel = koinViewModel(key = "random:${game?.value ?: "any"}", parameters = { parametersOf(game) }),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val opened = state.phase as? RandomPhase.Opened
    LaunchedEffect(opened) {
        val pick = opened?.pick ?: return@LaunchedEffect
        // A hide cut short (the host dropped the sheet) hands nothing on, but must not leave the view model on Opened.
        scope.launch { sheetState.hide() }.invokeOnCompletion { cause ->
            if (cause == null) onOpen(pick)
            viewModel.reset()
        }
    }
    // A list that changes under the open sheet (a game's archive loads or drops out) must not leave a game chosen that has no chip.
    LaunchedEffect(games, communityGames) {
        val now = viewModel.state.value
        val offered = if (now.source == RandomSource.Past) games else communityGames
        if (now.game != null && offered.none { it.id == now.game }) viewModel.chooseGame(null)
    }
    val pastGames by rememberUpdatedState(games)
    val newGames by rememberUpdatedState(communityGames)
    val actions = remember(viewModel) {
        RandomPuzzleActions(
            onSource = { source ->
                viewModel.chooseSource(source)
                val offered = if (source == RandomSource.Past) pastGames else newGames
                val chosen = viewModel.state.value.game
                if (chosen != null && offered.none { it.id == chosen }) viewModel.chooseGame(null)
            },
            onGame = viewModel::chooseGame,
            onUnsolved = viewModel::setUnsolvedOnly,
            onDifficulty = viewModel::chooseDifficulty,
            onPick = viewModel::pick,
        )
    }
    ModalBottomSheet(
        onDismissRequest = {
            viewModel.reset()
            onDismiss()
        },
        modifier = modifier.testTag("random.sheet"),
        sheetState = sheetState,
    ) {
        RandomPuzzleContent(state, if (state.source == RandomSource.Past) games else communityGames, layout, actions)
    }
}
