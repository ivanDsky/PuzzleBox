package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.LocalHintCostLine
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayLayout

/**
 * The regular play screen over a board the caller holds, outside the play store: the Studio's test solve (ticket 98)
 * and the Trainer's drill. Called as `SandboxPlay(ui, controller, title, label, layout, onBack, onSolved)`. It is the
 * screen a daily plays on (the same top bar, timer, board size and controls) with [PlayBoardChrome] drawing both, and
 * nothing a stored play adds: no tutorial, no How to play, no stats panel, no record. The bar reads `title`, the
 * board's name, then `label`, what the screen is: a name too long for the bar is cut with an ellipsis and the label
 * stays whole.
 *
 * `showClock` false leaves the timer out of the bar (the drill is not timed); the board's clock still runs, unseen.
 *
 * The solve plays the sweep, rests on the board (the play chrome's rest: no stamp, a tap skips it) and then
 * calls `onSolved`.
 * There is no BackHandler; the caller owns back. The tag is not a `route.` one: the screen sits inside the caller's
 * route.
 *
 * It is an object, not a top-level composable, because it needs a game's play UI and a live controller and so has no
 * preview of its own; `SandboxPlayGoldenScreenshots` draws it.
 */
object SandboxPlay {

    @Composable
    operator fun invoke(
        ui: GamePlayUi,
        controller: GamePlayController,
        title: String,
        label: String,
        layout: PlayLayout,
        onBack: () -> Unit,
        onSolved: () -> Unit,
        modifier: Modifier = Modifier,
        showClock: Boolean = true,
    ) {
        // The Studio test solve records no result, so it never says hints are counted on one.
        CompositionLocalProvider(LocalHintCostLine provides null) {
            PlayBoardChrome(
                ui = ui,
                controller = controller,
                status = PlayBoardStatus.Ready,
                title = title,
                label = label,
                pastDay = false,
                tutorial = TutorialGate.Closed,
                onHowToPlay = null,
                onTutorialFinished = {},
                layout = layout,
                onBack = onBack,
                onRetry = {},
                onSolved = { _, _ -> onSolved() },
                overflow = emptyList(),
                sidePanel = null,
                modifier = modifier.testTag("play.sandbox"),
                showClock = showClock,
            )
        }
    }
}
