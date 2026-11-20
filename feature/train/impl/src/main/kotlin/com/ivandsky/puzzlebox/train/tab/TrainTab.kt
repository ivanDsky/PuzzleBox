package com.ivandsky.puzzlebox.train.tab

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.train.practice.PracticeHost
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * The Games screen's Train tab body: the game row and the technique list. With [twoPane] a tap on a technique opens its
 * practice board in the right pane ([trainPane]) and the list stays; without it the board covers the list inside this
 * body, and back (the system's or the arrow's) returns to it.
 *
 * A factory, not a composable: the body is a Koin entry that cannot be previewed whole, so its previewed halves are
 * [TechniqueList] and the practice board, and the app gets this as the `@Composable () -> Unit` its Games slot takes.
 */
fun trainTab(twoPane: Boolean): @Composable () -> Unit = { TrainTabHost(twoPane) }

/**
 * The tablet's right pane while Games shows the Train tab: the practice board of the technique the list has open, or a
 * quiet prompt to pick one. It reads the same [TrainViewModel] as the list, so a tap on a row starts its practice board
 * here.
 */
fun trainPane(): @Composable (Modifier) -> Unit = { modifier -> TrainPaneHost(modifier) }

@Composable
internal fun TrainTabHost(twoPane: Boolean) {
    val vm: TrainViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val current = state ?: return
    val open = current.open
    if (!twoPane && open != null) {
        BackHandler(onBack = vm::closeTechnique)
        PracticeHost(open, showBack = true, onClose = vm::closeTechnique, modifier = Modifier.holdSideSwipes())
        return
    }
    val chips = rememberGameChips(current.games)
    TechniqueList(
        games = chips,
        selected = current.selected,
        techniques = current.techniques,
        openId = open?.technique?.id,
        onSelectGame = vm::selectGame,
        onOpen = { vm.openTechnique(current.selected, it) },
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * Takes every sideways drag inside the open practice board, so a stroke across a board (a line of crosses) never swipes
 * the Games pager to the next tab. A drag the board itself takes is already consumed and passes through untouched; taps
 * never pass the touch slop, so they are never taken.
 */
private fun Modifier.holdSideSwipes(): Modifier = pointerInput(Unit) {
    detectHorizontalDragGestures { change, _ -> change.consume() }
}

@Composable
internal fun TrainPaneHost(modifier: Modifier = Modifier) {
    val vm: TrainViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val open = state?.open
    if (open == null) {
        Box(modifier.fillMaxSize().testTag("train.placeholder"), contentAlignment = Alignment.Center) {
            Text("Pick a technique", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        PracticeHost(open, showBack = false, onClose = vm::closeTechnique, modifier = modifier)
    }
}

/** The game row's chips for [games]: each game's display name and brand, from the registries every game is in. */
@Composable
private fun rememberGameChips(games: List<GameId>): List<GameChip> {
    val names = koinInject<GameRegistry>()
    val plays = koinInject<GamePlayRegistry>()
    return remember(games, names, plays) { games.map { GameChip(it, names.displayName(it), plays.brandOf(it)) } }
}
