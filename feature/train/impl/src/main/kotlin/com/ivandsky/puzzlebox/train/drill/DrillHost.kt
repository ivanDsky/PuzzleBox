package com.ivandsky.puzzlebox.train.drill

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.train.tab.DrillRequest
import com.ivandsky.puzzlebox.train.tab.OpenTechnique
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * [open]'s drill on its own view model, one per game and technique so a board survives the screen turning. The drill
 * starts for the attempt [open] carries, once, and [request] says which puzzles to avoid. [showBack] puts a back arrow in
 * the title row, for the phone where the drill covers the technique list; [onClose] closes the drill, which drops the
 * board first. Back on either layout does the same: the drill is the one screen that always holds the system back.
 * [play] is the app's play screen for the board.
 */
@Composable
internal fun DrillHost(
    open: OpenTechnique,
    request: DrillRequest,
    showBack: Boolean,
    onClose: () -> Unit,
    play: DrillPlaySlot,
    modifier: Modifier = Modifier,
) {
    val game = open.game
    val technique = open.technique.id
    val vm: DrillViewModel = koinViewModel(
        key = "drill/${game.gameId.value}/${technique.value}",
        parameters = { parametersOf(game, technique) },
    )
    // Starting is idempotent per attempt and done before the first read of the state, so a drill opened again never shows
    // the previous board for a frame.
    remember(vm, open.attempt) { vm.start(open.attempt, request.avoid) }
    val state by vm.state.collectAsStateWithLifecycle()
    val close = remember(vm, onClose) {
        {
            vm.leave()
            onClose()
        }
    }
    BackHandler(onBack = close)
    DrillScreen(
        name = open.technique.name,
        state = state,
        showBack = showBack,
        onClose = close,
        play = play,
        onSolved = vm::solved,
        onTryAgain = vm::tryAgain,
        onTryAnother = vm::tryAnother,
        modifier = modifier,
    )
}
