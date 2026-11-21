package com.ivandsky.puzzlebox.train.practice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.train.tab.OpenTechnique
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * [open]'s practice, on its own view model, one per game and technique so a board survives the screen turning. Each
 * opening of the technique carries a new attempt number, and [PracticeViewModel.start] opens the technique's first board
 * for it; the chapters after it open from the result page's button, so the title row shows the technique the view model
 * is on, not [open]'s. The wait after a finished board runs only while the screen is shown: it stops when the app goes to
 * the background and goes on when it returns. [showBack] puts a back arrow in the title row, for the phone where the
 * board covers the technique list; [onClose] closes the practice. The game draws its own board and its own Spotlight;
 * this only frames them.
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
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.setForeground(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.setForeground(false) }
    val state by vm.state.collectAsStateWithLifecycle()
    val chapter by vm.chapter.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val hold by vm.hold.collectAsStateWithLifecycle()
    PracticePane(
        chapter = chapter,
        state = state,
        progress = progress,
        showBack = showBack,
        onClose = onClose,
        board = { board, boardModifier -> game.Board(board, boardModifier) },
        onTryAgain = vm::tryAgain,
        onTryAnother = vm::tryAnother,
        hold = hold,
        onSkip = vm::skip,
        onNextChapter = vm::nextChapter,
        onPracticeAgain = vm::practiceAgain,
        modifier = modifier,
    )
}
