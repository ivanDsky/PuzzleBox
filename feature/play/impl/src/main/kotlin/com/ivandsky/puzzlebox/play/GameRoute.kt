package com.ivandsky.puzzlebox.play

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.designsystem.AppDropdownMenu
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.gameui.PlayBoardFallback
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayClock
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.PlayOverflowItem
import com.ivandsky.puzzlebox.gameui.TimerBar
import com.ivandsky.puzzlebox.gameui.TutorialHost
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.GameKey
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * One [GameKey]'s board on any stack: the entry's [GameViewModel] opens the
 * slot's live play in the [PlaySessionStore], and this draws its board and status under
 * the shared play top bar: Up ([onBack], the same navigator pop as system back),
 * the game's glyph, its name and number ("Yesterday's puzzle" when the board was kept
 * across its game's midnight), then the timer, How to play and the game's overflow
 * settings. There is no BackHandler (predictive back stays the scene's) and no saveable
 * blob: the store's slot row is what survives process death.
 *
 * A game's first play shows its tutorial before the board, and the bar's How to play
 * reopens it over the live play; Skip or "Let's play" hands back to the board.
 * The bar's Up stays, so back leaves the game from a tutorial too.
 */
@Composable
internal fun GameRoute(
    key: GameKey,
    layout: PlayLayout,
    onBack: () -> Unit,
    onSolved: (slot: PlaySlot, timeMs: Long, hintsUsed: Int) -> Unit,
    linkedIn: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val viewModel: GameViewModel = koinViewModel(key = key.toString(), parameters = { parametersOf(key) })
    val pastDay by viewModel.pastDay.collectAsStateWithLifecycle()
    val tutorial by viewModel.tutorial.collectAsStateWithLifecycle()
    GameBoard(
        play = viewModel.play,
        pastDay = pastDay,
        tutorial = tutorial,
        onHowToPlay = viewModel::howToPlay,
        onTutorialFinished = viewModel::closeTutorial,
        layout = layout,
        onBack = onBack,
        onSolved = onSolved,
        overflow = emptyList(),
        linkedIn = linkedIn,
        modifier = modifier,
    )
}

/**
 * A friend's puzzle's board: the store's live play of the key's custom slot under the same
 * play top bar, without a tutorial gate. A friend's puzzle is never a past day.
 */
@Composable
internal fun GameRoute(
    key: CustomGameKey,
    layout: PlayLayout,
    onBack: () -> Unit,
    onSolved: (slot: PlaySlot, timeMs: Long, hintsUsed: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val store = koinInject<PlaySessionStore>()
    val play = remember(key) { store.open(key) }
    GameBoard(
        play = play,
        pastDay = false,
        tutorial = TutorialGate.Closed,
        onHowToPlay = null,
        onTutorialFinished = {},
        layout = layout,
        onBack = onBack,
        onSolved = onSolved,
        overflow = emptyList(),
        modifier = modifier,
    )
}

/** One live [play]'s top bar and board, behind the [tutorial] gate; How to play shows
 *  when [onHowToPlay] is given and the game has lessons, and the route's [overflow] items
 *  follow the game's own in the bar's menu. [linkedIn] ranks the day's LinkedIn connections into the side panel's times. */
@Composable
internal fun GameBoard(
    play: ActivePlay?,
    pastDay: Boolean,
    tutorial: TutorialGate,
    onHowToPlay: (() -> Unit)?,
    onTutorialFinished: () -> Unit,
    layout: PlayLayout,
    onBack: () -> Unit,
    onSolved: (slot: PlaySlot, timeMs: Long, hintsUsed: Int) -> Unit,
    overflow: List<PlayOverflowItem>,
    modifier: Modifier,
    linkedIn: Boolean = false,
) {
    Column(modifier = modifier.testTag("route.game")) {
        if (play == null) {
            PlayTopBar(title = if (pastDay) PAST_DAY else "", pastDay = pastDay, onBack = onBack)
            PlayBoardFallback(PlayBoardStatus.Unavailable(UNPLAYABLE), onRetry = {}, tagPrefix = "game")
            return@Column
        }
        val record by play.record.collectAsStateWithLifecycle()
        val board by play.board.collectAsStateWithLifecycle()
        val playing = tutorial == TutorialGate.Closed
        // A game's clock opens the moment its puzzle is adopted, which can be behind the
        // first-open tutorial: fold that window so lesson time never counts. The board's
        // chrome reopens it when the tutorial gives way to the Screen.
        val clockBehindTutorial = if (playing) null else board.controller as? PlayClock
        LaunchedEffect(clockBehindTutorial, board.status) { clockBehindTutorial?.onBackground() }
        PlayTopBar(
            title = if (pastDay) PAST_DAY else record.label,
            pastDay = pastDay,
            onBack = onBack,
            glyph = { GameGlyph(play.ui.brand, GameGlyphSize.Badge) },
        ) {
            if (!playing) return@PlayTopBar
            val clock = board.controller as? PlayClock
            // TwoPane's side panel draws the large live timer in place of this one.
            if (layout != PlayLayout.TwoPane && board.status is PlayBoardStatus.Ready && clock != null) {
                TimerBar(clock.displayedElapsedMs(), Modifier.width(IntrinsicSize.Max).testTag("play.timer"))
            }
            if (onHowToPlay != null && play.ui.tutorial.isNotEmpty()) {
                IconButton(onClick = onHowToPlay, modifier = Modifier.testTag("play.how_to_play")) {
                    Icon(
                        imageVector = PuzzleIcons.HowTo,
                        contentDescription = "How to play",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            PlayOverflow(play.ui.overflowFor(board.controller) + overflow)
        }
        when (tutorial) {
            TutorialGate.Checking -> PlayBoardFallback(PlayBoardStatus.Loading, onRetry = {}, tagPrefix = "game")
            is TutorialGate.Open -> TutorialHost(
                ui = play.ui,
                session = tutorial.session,
                onFinished = onTutorialFinished,
            )
            TutorialGate.Closed -> {
                // Phones never build the stats: only the two-pane side panel shows them.
                val stats: PlayStatsViewModel? = if (layout == PlayLayout.TwoPane) {
                    koinViewModel(key = "stats:${play.slot}", parameters = { parametersOf(play.slot, linkedIn) })
                } else {
                    null
                }
                val clock = (board.controller as? PlayClock)?.takeIf { board.status is PlayBoardStatus.Ready }
                val activityContext = LocalContext.current
                val sidePanel = remember(stats, clock, activityContext) { stats?.let { statsPanel(it, clock, activityContext) } }
                play.ui.Screen(
                    controller = board.controller,
                    status = board.status,
                    layout = layout,
                    sidePanel = sidePanel,
                    onRetry = play::retry,
                    onSolved = { timeMs, hintsUsed -> onSolved(play.slot, timeMs, hintsUsed) },
                    modifier = Modifier,
                )
            }
        }
    }
}

/**
 * The tablet side panel's content: [PlayStatsPanel] over [stats], under the game's
 * chrome, timing it off [clock] (null while the board isn't ready).
 */
private fun statsPanel(stats: PlayStatsViewModel, clock: PlayClock?, activityContext: Context): @Composable ColumnScope.() -> Unit = {
    val state by stats.state.collectAsStateWithLifecycle()
    val signIn by stats.signIn.collectAsStateWithLifecycle()
    PlayStatsPanel(
        state,
        elapsedMs = { clock?.displayedElapsedMs() },
        signIn = signIn,
        onSignIn = { stats.signIn(activityContext) },
    )
}

/** The game's frame while its open is still being decided: the top bar over the loading board. */
@Composable
internal fun GameLoading(onBack: () -> Unit) {
    Column {
        PlayTopBar(title = "", pastDay = false, onBack = onBack)
        PlayBoardFallback(PlayBoardStatus.Loading, onRetry = {}, tagPrefix = "game")
    }
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
 * The game's settings behind the bar's overflow button, e.g. Queens' Auto-cross.
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
        AppDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
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
