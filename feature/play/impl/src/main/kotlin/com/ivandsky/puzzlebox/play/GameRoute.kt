package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.gameui.LocalPlaySidePanel
import com.ivandsky.puzzlebox.gameui.PlayBoardFallback
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayClock
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.PlayOverflowItem
import com.ivandsky.puzzlebox.gameui.TimerBar
import com.ivandsky.puzzlebox.gameui.TutorialHost
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.api.GameKey
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * One [GameKey]'s board on any stack (A9, N21): the entry's [GameViewModel] opens the
 * slot's live play in the [PlaySessionStore], and this draws its board and status under
 * the shared play top bar (A14): Up ([onBack], the same navigator pop as system back),
 * the game's glyph, its name and number ("Yesterday's puzzle" when the board was kept
 * across its game's midnight), then the timer, How to play and the game's overflow
 * settings. There is no BackHandler (predictive back stays the scene's) and no saveable
 * blob: the store's slot row is what survives process death (A8, A11).
 *
 * A game's first play shows its tutorial before the board, and the bar's How to play
 * reopens it over the live play (spec A11); Skip or "Let's play" hands back to the board.
 * The bar's Up stays, so back leaves the game from a tutorial too.
 */
@Composable
internal fun GameRoute(
    key: GameKey,
    layout: PlayLayout,
    onBack: () -> Unit,
    onSolved: (slot: PlaySlot, timeMs: Long, hintsUsed: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: GameViewModel = koinViewModel(key = key.toString(), parameters = { parametersOf(key) })
    val play = viewModel.play
    val pastDay by viewModel.pastDay.collectAsStateWithLifecycle()
    Column(modifier = modifier.testTag("route.game")) {
        if (play == null) {
            PlayTopBar(title = if (pastDay) PAST_DAY else "", pastDay = pastDay, onBack = onBack)
            PlayBoardFallback(PlayBoardStatus.Unavailable(UNPLAYABLE), onRetry = {}, tagPrefix = "game")
            return@Column
        }
        val record by play.record.collectAsStateWithLifecycle()
        val tutorial by viewModel.tutorial.collectAsStateWithLifecycle()
        val board by play.board.collectAsStateWithLifecycle()
        val playing = tutorial == TutorialGate.Closed
        PlayTopBar(
            title = if (pastDay) PAST_DAY else record.label,
            pastDay = pastDay,
            onBack = onBack,
            glyph = { GameGlyph(play.ui.brand, GameGlyphSize.Badge) },
        ) {
            if (!playing) return@PlayTopBar
            val clock = board.controller as? PlayClock
            // TwoPane's side panel draws the large live timer in place of this one (A15).
            if (layout != PlayLayout.TwoPane && board.status is PlayBoardStatus.Ready && clock != null) {
                TimerBar(clock.displayedElapsedMs(), Modifier.width(IntrinsicSize.Max).testTag("play.timer"))
            }
            if (play.ui.tutorial.isNotEmpty()) {
                IconButton(onClick = viewModel::howToPlay, modifier = Modifier.testTag("play.how_to_play")) {
                    Icon(
                        imageVector = PuzzleIcons.HowTo,
                        contentDescription = "How to play",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            PlayOverflow(play.ui.overflowFor(board.controller))
        }
        when (val gate = tutorial) {
            TutorialGate.Checking -> PlayBoardFallback(PlayBoardStatus.Loading, onRetry = {}, tagPrefix = "game")
            is TutorialGate.Open -> TutorialHost(
                ui = play.ui,
                session = gate.session,
                onFinished = viewModel::closeTutorial,
            )
            TutorialGate.Closed -> StatsSidePanel(
                layout = layout,
                slot = play.slot,
                clock = (board.controller as? PlayClock)?.takeIf { board.status is PlayBoardStatus.Ready },
            ) {
                play.ui.Screen(
                    controller = board.controller,
                    status = board.status,
                    layout = layout,
                    onRetry = play::retry,
                    onSolved = { timeMs, hintsUsed -> onSolved(play.slot, timeMs, hintsUsed) },
                    modifier = Modifier,
                )
            }
        }
    }
}

/**
 * [content] with the tablet side panel's stats (A15) in [LocalPlaySidePanel]: a TwoPane
 * play scaffold draws [PlayStatsPanel] under the game's chrome, timing it off [clock]
 * (null while the board isn't ready). A single pane composes [content] alone, so phones
 * never build the stats.
 */
@Composable
private fun StatsSidePanel(layout: PlayLayout, slot: PlaySlot, clock: PlayClock?, content: @Composable () -> Unit) {
    if (layout != PlayLayout.TwoPane) {
        content()
        return
    }
    val stats: PlayStatsViewModel = koinViewModel(key = "stats:$slot", parameters = { parametersOf(slot) })
    val panel: @Composable ColumnScope.() -> Unit = remember(stats, clock) {
        {
            val state by stats.state.collectAsStateWithLifecycle()
            PlayStatsPanel(state, elapsedMs = { clock?.displayedElapsedMs() })
        }
    }
    CompositionLocalProvider(LocalPlaySidePanel provides panel, content = content)
}

/** The play top bar: Up, the game's [glyph] and [title], then the play [actions]. */
@Composable
private fun PlayTopBar(
    title: String,
    pastDay: Boolean,
    onBack: () -> Unit,
    glyph: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    CompactTopBar(
        title = title,
        modifier = if (pastDay) Modifier.testTag("game.past_day") else Modifier,
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("game.up")) {
                Icon(
                    imageVector = PuzzleIcons.Back,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            glyph()
        },
        actions = actions,
    )
}

/**
 * The game's settings behind the bar's overflow button (A14), e.g. Queens' Auto-cross.
 * A toggle shows a check while on; picking an item closes the menu. No items, no button.
 */
@Composable
private fun PlayOverflow(items: List<PlayOverflowItem>) {
    if (items.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("play.overflow")) {
            Icon(
                imageVector = PuzzleIcons.More,
                contentDescription = "More",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { item ->
                val check: (@Composable () -> Unit)? =
                    if (item.checked == true) ({ Icon(PuzzleIcons.Check, contentDescription = "On") }) else null
                DropdownMenuItem(
                    text = { Text(item.label) },
                    onClick = {
                        open = false
                        item.onClick()
                    },
                    modifier = Modifier.testTag("play.overflow.item"),
                    trailingIcon = check,
                )
            }
        }
    }
}

private const val UNPLAYABLE = "This game isn't available in this build."
private const val PAST_DAY = "Yesterday's puzzle"
