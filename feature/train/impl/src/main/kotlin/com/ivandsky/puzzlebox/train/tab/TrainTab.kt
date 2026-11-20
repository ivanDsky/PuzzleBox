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
import com.ivandsky.puzzlebox.train.drill.DrillHost
import com.ivandsky.puzzlebox.train.drill.DrillPlaySlot
import com.ivandsky.puzzlebox.train.round.RoundHost
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * The Games screen's Train tab body: the game row and the technique list. With [twoPane] a tap on a technique opens its
 * round in the right pane ([trainPane]) and the list stays; without it the round covers the list inside this body, and
 * back (the system's or the arrow's) returns to it. The round's summary goes on to a full puzzle of the technique, which
 * takes the round's place and is drawn by [drillPlay], the app's play screen for it.
 *
 * A factory, not a composable: the body is a Koin entry that cannot be previewed whole, so its previewed halves are
 * [TechniqueList] and the round, and the app gets this as the `@Composable () -> Unit` its Games slot takes.
 */
fun trainTab(twoPane: Boolean, drillPlay: DrillPlaySlot): @Composable () -> Unit = { TrainTabHost(twoPane, drillPlay) }

/**
 * The tablet's right pane while Games shows the Train tab: the round or drill of the technique the list has open, or a
 * quiet prompt to pick one. It reads the same [TrainViewModel] as the list, so a tap on a row starts its round here.
 */
fun trainPane(drillPlay: DrillPlaySlot): @Composable (Modifier) -> Unit = { modifier -> TrainPaneHost(drillPlay, modifier) }

@Composable
internal fun TrainTabHost(twoPane: Boolean, drillPlay: DrillPlaySlot) {
    val vm: TrainViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val current = state ?: return
    val open = current.open
    if (!twoPane && open != null) {
        BackHandler(onBack = vm::closeTechnique)
        OpenTechniqueBody(open, vm, drillPlay, showBack = true, modifier = Modifier.holdSideSwipes())
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
 * Takes every sideways drag inside the open round or drill, so a stroke across a board (a line of crosses) never swipes
 * the Games pager to the next tab. A drag the board itself takes is already consumed and passes through untouched;
 * taps never pass the touch slop, so they are never taken.
 */
private fun Modifier.holdSideSwipes(): Modifier = pointerInput(Unit) {
    detectHorizontalDragGestures { change, _ -> change.consume() }
}

@Composable
internal fun TrainPaneHost(drillPlay: DrillPlaySlot, modifier: Modifier = Modifier) {
    val vm: TrainViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val open = state?.open
    if (open == null) {
        Box(modifier.fillMaxSize().testTag("train.placeholder"), contentAlignment = Alignment.Center) {
            Text("Pick a technique", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        OpenTechniqueBody(open, vm, drillPlay, showBack = false, modifier = modifier)
    }
}

/** [open]'s round, or its drill once the summary went on to one. */
@Composable
private fun OpenTechniqueBody(
    open: OpenTechnique,
    vm: TrainViewModel,
    drillPlay: DrillPlaySlot,
    showBack: Boolean,
    modifier: Modifier = Modifier,
) {
    val drill = open.drill
    if (drill == null) {
        RoundHost(open, showBack, onClose = vm::closeTechnique, onDrill = vm::openDrill, modifier = modifier)
    } else {
        DrillHost(open, drill, showBack, onClose = vm::closeTechnique, play = drillPlay, modifier = modifier)
    }
}

/** The game row's chips for [games]: each game's display name and brand, from the registries every game is in. */
@Composable
private fun rememberGameChips(games: List<GameId>): List<GameChip> {
    val names = koinInject<GameRegistry>()
    val plays = koinInject<GamePlayRegistry>()
    return remember(games, names, plays) { games.map { GameChip(it, names.displayName(it), plays.brandOf(it)) } }
}
