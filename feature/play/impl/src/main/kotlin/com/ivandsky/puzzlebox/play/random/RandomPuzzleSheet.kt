package com.ivandsky.puzzlebox.play.random

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
 * Pick a random past puzzle: [game] is the preset chip (null is Any), [games] the chips on offer. Once a pick opened and
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
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onOpen(pick)
            viewModel.reset()
        }
    }
    val actions = remember(viewModel) {
        RandomPuzzleActions(onGame = viewModel::chooseGame, onUnsolved = viewModel::setUnsolvedOnly, onPick = viewModel::pick)
    }
    ModalBottomSheet(
        onDismissRequest = {
            viewModel.reset()
            onDismiss()
        },
        modifier = modifier.testTag("random.sheet"),
        sheetState = sheetState,
    ) {
        RandomPuzzleContent(state, games, layout, actions)
    }
}
