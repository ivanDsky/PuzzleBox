package com.ivandsky.puzzlebox.train.round

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.train.tab.OpenTechnique
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * [open]'s round, on its own view model, one per game and technique so a round survives the screen turning. Each
 * opening of the technique carries a new attempt number, and [RoundViewModel.start] builds a fresh round for it.
 * [showBack] puts a back arrow in the title row, for the phone where the round covers the technique list; [onClose]
 * closes the round; [onDrill] opens the technique's full-puzzle drill, given the archive numbers this round loaded so the
 * drill can prefer another.
 */
@Composable
internal fun RoundHost(
    open: OpenTechnique,
    showBack: Boolean,
    onClose: () -> Unit,
    onDrill: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val game = open.game
    val technique = open.technique.id
    val vm: RoundViewModel = koinViewModel(
        key = "${game.gameId.value}/${technique.value}",
        parameters = { parametersOf(game, technique) },
    )
    // Starting is idempotent per attempt and done before the first read of the state, so reopening a technique never
    // shows the previous round for a frame.
    remember(vm, open.attempt) { vm.start(open.attempt) }
    val state by vm.state.collectAsStateWithLifecycle()
    val board = remember(vm, game) {
        RoundBoard(
            board = { snippet, boardModifier -> game.Board(snippet, { verdict -> vm.onVerdict(snippet, verdict) }, boardModifier) },
            spotlight = game::spotlight,
        )
    }
    RoundPane(
        name = open.technique.name,
        line = open.technique.line,
        state = state,
        showBack = showBack,
        onClose = onClose,
        board = board,
        onShowMe = vm::showMe,
        onNext = vm::next,
        onTryAgain = vm::tryAgain,
        onTryAnother = vm::tryAnother,
        onDrill = { onDrill(vm.usedNumbers) },
        modifier = modifier,
    )
}
