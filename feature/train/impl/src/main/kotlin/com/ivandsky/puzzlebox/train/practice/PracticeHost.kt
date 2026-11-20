package com.ivandsky.puzzlebox.train.practice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.train.tab.OpenTechnique
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * [open]'s practice board, on its own view model, one per game and technique so a board survives the screen turning. Each
 * opening of the technique carries a new attempt number, and [PracticeViewModel.start] opens a fresh board for it.
 * [showBack] puts a back arrow in the title row, for the phone where the board covers the technique list; [onClose]
 * closes the practice. The game draws its own board and its own Spotlight; this only frames them.
 */
@Composable
internal fun PracticeHost(
    open: OpenTechnique,
    showBack: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val game = open.game
    val technique = open.technique.id
    val vm: PracticeViewModel = koinViewModel(
        key = "${game.gameId.value}/${technique.value}",
        parameters = { parametersOf(game, technique) },
    )
    // Starting is idempotent per attempt and done before the first read of the state, so reopening a technique never
    // shows the previous board for a frame.
    remember(vm, open.attempt) { vm.start(open.attempt) }
    val state by vm.state.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    PracticePane(
        name = open.technique.name,
        line = open.technique.line,
        state = state,
        progress = progress,
        showBack = showBack,
        onClose = onClose,
        board = { board, boardModifier -> game.Board(board, boardModifier) },
        onNextBoard = vm::nextBoard,
        onTryAgain = vm::tryAgain,
        onTryAnother = vm::tryAnother,
        modifier = modifier,
    )
}
