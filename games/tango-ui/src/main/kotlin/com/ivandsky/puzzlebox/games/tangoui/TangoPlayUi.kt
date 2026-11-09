package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.AdaptivePlayScaffold
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayActionBar
import com.ivandsky.puzzlebox.gameui.PlayBoardFallback
import com.ivandsky.puzzlebox.gameui.PlayBoardMaxWidth
import com.ivandsky.puzzlebox.gameui.PlayBoardPane
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayChrome
import com.ivandsky.puzzlebox.gameui.PlayClear
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.PlaySolveFrame
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.gameui.hint.HintModeSource
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.model.GameId

/** Tango's one play registration (ADR-0001): its controller, its board and its play screen. */
class TangoPlayUi(private val hintMode: HintModeSource = HintModeSource { HintMode.Legacy }) : GamePlayUi {
    override val gameId: GameId get() = TangoDefinition.id

    override val brand: GameBrand get() = TangoBrand

    override fun createController(env: PlayEnvironment): GamePlayController =
        TangoPlayController(env, hintMode = hintMode.current())

    /** Clear wipes the player's cells as one undo unit; always enabled (an empty or solved board ignores it). */
    override fun clearFor(controller: GamePlayController): PlayClear = tangoClear(controller as TangoPlayController)

    override val tutorial: List<TutorialStep> get() = TangoTutorial.steps

    @Composable
    override fun Board(controller: GamePlayController, modifier: Modifier) {
        // The tutorial host draws no toolbar pane, so the board keeps Tango's own cap.
        TangoBoard(controller as TangoPlayController, paneCap = null, modifier)
    }

    @Composable
    override fun Screen(
        controller: GamePlayController,
        status: PlayBoardStatus,
        layout: PlayLayout,
        sidePanel: (@Composable ColumnScope.() -> Unit)?,
        onRetry: () -> Unit,
        onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
        modifier: Modifier,
    ) {
        TangoPlayScreen(controller as TangoPlayController, status, layout, sidePanel, onRetry, onSolved, modifier)
    }
}

/**
 * The Tango play screen (tango-05, A14): the Zip play shell (ticket 13/23 semantics) with
 * 04's board renderer. Taps land through [TangoPlayController.onTap] (the cycle step read
 * off the live session); the caption is the shared [PlayChrome] and the controls the shared
 * [PlayActionBar], whose Clear wipes the player's cells. Design §6: the win moment is the same
 * [PlaySolveFrame] then route handoff; this screen never renders win content.
 */
@Composable
fun TangoPlayScreen(
    controller: TangoPlayController,
    status: PlayBoardStatus,
    layout: PlayLayout,
    sidePanel: (@Composable ColumnScope.() -> Unit)?,
    onRetry: () -> Unit,
    onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = status as? PlayBoardStatus.Ready
    Box(modifier.fillMaxSize()) {
        AdaptivePlayScaffold(
            layout = layout,
            modifier = Modifier.fillMaxSize(),
            board = { paneCap -> TangoBoardArea(controller, status, paneCap, onRetry, onSolved) },
            side = {
                PlayChrome(
                    clock = controller,
                    hints = controller,
                    banner = ready?.banner,
                    feedback = controller,
                )
            },
            actions = {
                PlayActionBar(controller, controller, remember(controller) { tangoClear(controller) }, TangoBrand.accent)
            },
            sidePanel = sidePanel,
        )
    }
}

private fun tangoClear(controller: TangoPlayController): PlayClear = PlayClear(controller::clearBoard)

/** The board pane: Tango's one board once a puzzle is ready, the shared fallback otherwise. */
@Composable
private fun BoxScope.TangoBoardArea(
    controller: TangoPlayController,
    status: PlayBoardStatus,
    paneCap: Dp?,
    onRetry: () -> Unit,
    onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
) {
    PlayBoardPane(
        Modifier
            .testTag("play.board_area")
            // A4: standard pane margins; the cap below is a max, not a fixed width.
            .padding(horizontal = Spacing.lg),
    ) {
        val session = controller.session
        if (status is PlayBoardStatus.Ready && session != null) {
            // The win moment: the one-shot route handoff (ticket-23 pattern) after the
            // solve frame; the record write keeps running in the store.
            PlaySolveFrame(
                brand = TangoBrand,
                solved = session.completedAt,
                onHandOff = {
                    controller.session?.takeIf { it.isComplete }?.let { onSolved(it.elapsedMs, it.hintsUsed) }
                },
            ) {
                TangoBoard(controller, paneCap, Modifier)
            }
        } else {
            PlayBoardFallback(status, onRetry, tagPrefix = "play")
        }
    }
}

/**
 * Tango's one board, chrome-free ([GamePlayUi.Board]): the daily pane and the tutorial
 * host both draw it, so a lesson's taps are the daily ones. Capped at [paneCap] in a
 * two-pane toolbar pane, [PlayBoardMaxWidth] otherwise. Draws nothing until [controller]
 * holds a session.
 */
@Composable
private fun TangoBoard(controller: TangoPlayController, paneCap: Dp?, modifier: Modifier) {
    // suppressedSets is a key: the settle debounce clears it 0.5 s after the last touch,
    // and the model must rebuild then or newly introduced violations stay hidden forever.
    val model = remember(
        controller,
        controller.session,
        controller.hintCell,
        controller.lastRejection,
        controller.suppressedSets,
        controller.guidance.answer,
    ) {
        controller.rendererModel()
    } ?: return
    // Taps go to the controller by cell index, so the cycle step reads the live session
    // (M4-tango-28); TalkBack actions land as placements. The outline
    // rides the board modifier (§2 — the Zip outline pattern).
    val renderer = remember(controller) {
        TangoBoardRenderer(
            onTap = controller::onTap,
            onPlacement = controller::onPlacement,
            spotlight = { controller.spotlight },
        )
    }
    val cap = paneCap ?: PlayBoardMaxWidth
    renderer.Content(
        model = model,
        modifier = modifier
            .widthIn(max = cap)
            .heightIn(max = cap)
            // No zipBoardOutline here: that's Zip's ROUNDED outline. Tango's
            // squared mesh closes itself with a square border (renderer).
            .testTag("play.board"),
    )
}
