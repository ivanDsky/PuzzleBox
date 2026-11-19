package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.LocalSolveRestOnBoard
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayClock
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.PlayOverflowItem
import com.ivandsky.puzzlebox.gameui.TutorialHost
import com.ivandsky.puzzlebox.resultui.TimerBar
import com.ivandsky.puzzlebox.resultui.selfTimerLabel

/**
 * One board's play screen, whoever holds its [controller]: the play top bar (Up, the game's glyph and [title], then
 * the timer, How to play and the overflow) over the game's own Screen, behind the [tutorial] gate. A [label] follows the title and is never cut; the title gives way to it. How to play shows
 * when [onHowToPlay] is given and the game has lessons, and the caller's [overflow] items follow the game's own in
 * the bar's menu.
 *
 * [sidePanel] builds the two-pane side panel; it is called only while the board plays.
 * A [targetMs] (a self-duel leg's past you's time) makes the bar's timer count toward it as time left, then over by.
 * While [rules] is set the rules sheet shows over the live board ([RulesSheetHost]).
 *
 * Under it a solve rests on the board before [onSolved] ([LocalSolveRestOnBoard]), for every board it draws.
 */
@Composable
internal fun PlayBoardChrome(
    ui: GamePlayUi,
    controller: GamePlayController,
    status: PlayBoardStatus,
    title: String,
    label: String?,
    pastDay: Boolean,
    tutorial: TutorialGate,
    onHowToPlay: (() -> Unit)?,
    onTutorialFinished: () -> Unit,
    layout: PlayLayout,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
    overflow: List<PlayOverflowItem>,
    sidePanel: (@Composable () -> (@Composable ColumnScope.() -> Unit)?)?,
    modifier: Modifier,
    targetMs: Long? = null,
    rules: RulesSheetCopy? = null,
    onDismissRules: () -> Unit = {},
    onReplayTutorial: () -> Unit = {},
) {
    CompositionLocalProvider(LocalSolveRestOnBoard provides true) {
        PlayBoardHosts {
            Column(modifier = modifier) {
                val playing = tutorial == TutorialGate.Closed
                // A game's clock opens the moment its puzzle is adopted, which can be behind the
                // first-open tutorial: fold that window so lesson time never counts. The board's
                // chrome reopens it when the tutorial gives way to the Screen.
                val clockBehindTutorial = if (playing) null else controller as? PlayClock
                LaunchedEffect(clockBehindTutorial, status) { clockBehindTutorial?.onBackground() }
                PlayTopBar(
                    title = title,
                    label = label,
                    pastDay = pastDay,
                    onBack = onBack,
                    glyph = { GameGlyph(ui.brand, GameGlyphSize.Badge) },
                ) {
                    if (!playing) return@PlayTopBar
                    val clock = controller as? PlayClock
                    if (status is PlayBoardStatus.Ready && clock != null) {
                        TimerBar(
                            elapsedMs = { clock.displayedElapsedMs() },
                            modifier = Modifier.width(IntrinsicSize.Max).testTag("play.timer"),
                            budgetMs = targetMs,
                            label = if (targetMs != null) ::selfTimerLabel else null,
                        )
                    }
                    if (onHowToPlay != null && ui.tutorial.isNotEmpty()) {
                        IconButton(onClick = onHowToPlay, modifier = Modifier.testTag("play.how_to_play")) {
                            Icon(
                                imageVector = PuzzleIcons.HowTo,
                                contentDescription = "How to play",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    PlayOverflow(ui.overflowFor(controller) + overflow)
                }
                when (tutorial) {
                    is TutorialGate.Open -> TutorialHost(
                        ui = ui,
                        session = tutorial.session,
                        onFinished = onTutorialFinished,
                    )
                    TutorialGate.Closed -> {
                        val clock = (controller as? PlayClock)?.takeIf { status is PlayBoardStatus.Ready }
                        ui.Screen(
                            controller = controller,
                            status = status,
                            layout = layout,
                            sidePanel = sidePanel?.invoke(),
                            onRetry = onRetry,
                            onSolved = onSolved,
                            modifier = Modifier,
                        )
                    }
                }
                RulesSheetHost(ui, controller, rules, playing, onDismissRules, onReplayTutorial)
            }
        }
    }
}
