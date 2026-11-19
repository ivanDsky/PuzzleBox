package com.ivandsky.puzzlebox.play.random

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.designsystem.AppModalBottomSheet
import com.ivandsky.puzzlebox.designsystem.FilterPageSwap
import com.ivandsky.puzzlebox.play.domain.random.RandomPick
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Pick a random puzzle: [game] is the preset chip, [games] the Game chips of Past puzzles and
 * [communityGames] those of New today (see [communityGamesOf]). Switching source keeps the chosen game when the new source
 * offers it, else falls back to Any. Once a pick opened and
 * the sheet hid, [onOpen] gets it and the sheet goes back to idle; an outcome that opened nothing says why in the sheet
 * and leaves the filters as they were. A swipe or the scrim cancels a pick in flight, then [onDismiss] runs.
 *
 * With the view model's [RandomPuzzleViewModel.filterSheets] the Game and Difficulty chips swap the sheet's content for their option
 * page in place ([FilterPageSwap]); the open page survives a rotation, and Back (the arrow or the system's) returns to the chips.
 *
 * Motion: the sheet's **Slide in / Slide out** and the scrim's **Fade** are ModalBottomSheet's own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RandomPuzzleSheet(
    game: GameId,
    games: List<RandomGame>,
    communityGames: List<RandomGame>,
    layout: PlayLayout,
    onOpen: (RandomPick) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RandomPuzzleViewModel = koinViewModel(key = "random:${game.value}", parameters = { parametersOf(game) }),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf<RandomFilterPage?>(null) }
    val opened = state.phase as? RandomPhase.Opened
    LaunchedEffect(opened) {
        val pick = opened?.pick ?: return@LaunchedEffect
        // A hide cut short (the host dropped the sheet) hands nothing on, but must not leave the view model on Opened.
        scope.launch { sheetState.hide() }.invokeOnCompletion { cause ->
            if (cause == null) onOpen(pick)
            viewModel.reset()
        }
    }
    // A game that was offered and then leaves the list under the open sheet must not stay chosen with no chip. A game never
    // offered (a preset or a restored one while the list is still loading) stays: its chip comes when the list does.
    val everOffered = remember { mutableSetOf<GameId>() }
    LaunchedEffect(games, communityGames) {
        everOffered += games.map { it.id }
        everOffered += communityGames.map { it.id }
        val now = viewModel.state.value
        val offered = if (now.source == RandomSource.Past) games else communityGames
        val chosen = now.game ?: return@LaunchedEffect
        if (chosen in everOffered && offered.none { it.id == chosen }) viewModel.chooseGame(null)
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
    AppModalBottomSheet(
        onDismissRequest = {
            viewModel.reset()
            onDismiss()
        },
        modifier = modifier.testTag("random.sheet"),
        sheetState = sheetState,
    ) {
        val shown = if (state.source == RandomSource.Past) games else communityGames
        if (viewModel.filterSheets) {
            FilterPageSwap(page, onBack = { page = null }, pageContent = { RandomFilterPageContent(it, state, shown, actions, onBack = { page = null }) }) {
                RandomPuzzleContent(state, shown, layout, actions, filterSheets = true, onOpenFilter = { page = it })
            }
        } else {
            RandomPuzzleContent(state, shown, layout, actions)
        }
    }
}
