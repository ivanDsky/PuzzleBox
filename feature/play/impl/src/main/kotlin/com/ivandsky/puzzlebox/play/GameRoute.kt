package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.gameui.PlayBoardFallback
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayHints
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.PlayOverflowItem
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.resultui.board.DailyBoardStyle
import org.koin.androidx.compose.koinViewModel
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
 * A game's first play opens its tutorial in place of the board once the seen-check lands, and
 * the bar's How to play reopens it over the live play (or, with RulesSheet on, opens the rules sheet there, which offers
 * the tutorial); Skip or "Let's play" hands back to the board.
 * The bar's Up stays, so back leaves the game from a tutorial too. [boardStyle] is the side panel's
 * times' (see [PlayTimesCard]).
 */
@Composable
internal fun GameRoute(
    key: GameKey,
    layout: PlayLayout,
    onBack: () -> Unit,
    onSolved: (slot: PlaySlot, timeMs: Long, hintsUsed: Int) -> Unit,
    linkedIn: Boolean = false,
    modifier: Modifier = Modifier,
    boardStyle: DailyBoardStyle = DailyBoardStyle.defaults(),
) {
    val viewModel: GameViewModel = koinViewModel(key = key.toString(), parameters = { parametersOf(key) })
    val pastDay by viewModel.pastDay.collectAsStateWithLifecycle()
    val tutorial by viewModel.tutorial.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    GameBoard(
        play = viewModel.play,
        pastDay = pastDay,
        tutorial = tutorial,
        boardStyle = boardStyle,
        onHowToPlay = viewModel::howToPlay,
        onTutorialFinished = viewModel::closeTutorial,
        rules = rules,
        onDismissRules = viewModel::dismissRules,
        onReplayTutorial = viewModel::replayTutorial,
        layout = layout,
        onBack = onBack,
        onSolved = onSolved,
        overflow = emptyList(),
        linkedIn = linkedIn,
        modifier = modifier,
    )
}

/**
 * A friend's puzzle's board: the live play of the key's custom slot under the same play top bar, behind
 * the daily's tutorial gate when PlayFeel is on ([CustomGameViewModel]). A friend's puzzle is never a past day.
 */
@Composable
internal fun GameRoute(
    key: CustomGameKey,
    layout: PlayLayout,
    onBack: () -> Unit,
    onSolved: (slot: PlaySlot, timeMs: Long, hintsUsed: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CustomGameViewModel = koinViewModel(key = key.toString(), parameters = { parametersOf(key) })
    val tutorial by viewModel.tutorial.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    GameBoard(
        play = viewModel.play,
        pastDay = false,
        tutorial = tutorial,
        onHowToPlay = viewModel.howToPlay,
        onTutorialFinished = viewModel::closeTutorial,
        rules = rules,
        onDismissRules = viewModel::dismissRules,
        onReplayTutorial = viewModel::replayTutorial,
        layout = layout,
        onBack = onBack,
        onSolved = onSolved,
        overflow = emptyList(),
        modifier = modifier,
    )
}

/** One live [play]'s top bar and board ([PlayBoardChrome]), behind the [tutorial] gate; How to play shows
 *  when [onHowToPlay] is given and the game has lessons, and the route's [overflow] items
 *  follow the game's own in the bar's menu. [linkedIn] ranks the day's LinkedIn connections into the side panel's times
 *  and [boardStyle] is how their board looks (see [PlayTimesCard]). A [selfLeg] puts past you's time on the timer
 *  and, on two panes, swaps the side panel for the self-duel leg's own ([legSidePanel]). While [rules] is set the rules sheet
 *  shows over the board ([PlayBoardChrome]). */
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
    boardStyle: DailyBoardStyle = DailyBoardStyle.defaults(),
    selfLeg: SelfLeg? = null,
    rules: RulesSheetCopy? = null,
    onDismissRules: () -> Unit = {},
    onReplayTutorial: () -> Unit = {},
) {
    if (play == null) {
        Column(modifier = modifier.testTag("route.game")) {
            PlayTopBar(title = if (pastDay) PAST_DAY else "", pastDay = pastDay, onBack = onBack)
            PlayBoardFallback(PlayBoardStatus.Unavailable(UNPLAYABLE), onRetry = {}, tagPrefix = "game")
        }
        return
    }
    val record by play.record.collectAsStateWithLifecycle()
    val board by play.board.collectAsStateWithLifecycle()
    HintCostHost(play.ui.gameId, play.inputs) {
        PlayBoardChrome(
            ui = play.ui,
            controller = board.controller,
            status = board.status,
            title = if (pastDay) PAST_DAY else record.label,
            label = null,
            pastDay = pastDay,
            tutorial = tutorial,
            onHowToPlay = onHowToPlay,
            onTutorialFinished = onTutorialFinished,
            layout = layout,
            onBack = onBack,
            onRetry = play::retry,
            onSolved = { timeMs, hintsUsed -> onSolved(play.slot, timeMs, hintsUsed) },
            overflow = overflow,
            sidePanel = {
                // Phones never build the stats: only the two-pane side panel shows them.
                val stats: PlayStatsViewModel? = if (layout == PlayLayout.TwoPane && selfLeg == null) {
                    koinViewModel(key = "stats:${play.slot}", parameters = { parametersOf(play.slot, linkedIn) })
                } else {
                    null
                }
                val activityContext = LocalContext.current
                val controller = board.controller
                remember(stats, activityContext, boardStyle, selfLeg, controller, layout) {
                    when {
                        selfLeg != null && layout == PlayLayout.TwoPane ->
                            legSidePanel(selfLeg, { (controller as? PlayHints)?.hintsUsed ?: 0 })
                        else -> stats?.let { statsPanel(it, activityContext, boardStyle) }
                    }
                }
            },
            modifier = modifier.testTag("route.game"),
            targetMs = selfLeg?.target?.inWholeMilliseconds,
            rules = rules,
            onDismissRules = onDismissRules,
            onReplayTutorial = onReplayTutorial,
        )
    }
}

private const val UNPLAYABLE = "This game isn't available in this build."
private const val PAST_DAY = "Yesterday's puzzle"
