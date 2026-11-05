package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.AdaptivePlayScaffold
import com.ivandsky.puzzlebox.gameui.DragTraceBoard
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.PlayBoardFallback
import com.ivandsky.puzzlebox.gameui.PlayBoardMaxWidth
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayChrome
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.gameui.rememberReducedMotion
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.delay

/** Ticket 28: the hint arrow's on-screen timing — hold ~2s, then a short fade. */
private const val HINT_ARROW_HOLD_MS = 2000L
private const val HINT_ARROW_FADE_MS = 300

/** Zip's one play registration (ADR-0001): its controller, its board and its play screen. */
class ZipPlayUi : GamePlayUi {
    override val gameId: GameId get() = ZipDefinition.id

    override fun createController(env: PlayEnvironment): GamePlayController = ZipPlayController(env)

    // Tickets 14–16 fill the lessons.
    override val tutorial: List<TutorialStep> get() = emptyList()

    @Composable
    override fun Board(controller: GamePlayController, modifier: Modifier) {
        ZipBoard(controller as ZipPlayController, modifier)
    }

    @Composable
    override fun Screen(
        controller: GamePlayController,
        status: PlayBoardStatus,
        layout: PlayLayout,
        onRetry: () -> Unit,
        onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
        modifier: Modifier,
    ) {
        ZipPlayScreen(controller as ZipPlayController, status, layout, onRetry, onSolved, modifier)
    }
}

/**
 * The Zip play screen (ticket 13): board + trace gesture + timer + hints, arranged by
 * the 07 adaptive policy (two-pane needs Expanded width AND landscape). State flows one
 * way: [controller] owns the session; the screen draws and forwards events.
 *
 * Ticket 23: the win moment is a ROUTE handoff, not an overlay — when the session
 * completes, [onSolved] carries the frozen A3 facts to the result route; this screen
 * never renders win content itself.
 */
@Composable
fun ZipPlayScreen(
    controller: ZipPlayController,
    status: PlayBoardStatus,
    layout: PlayLayout,
    onRetry: () -> Unit,
    onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = status as? PlayBoardStatus.Ready

    // The win moment (ticket 23): a one-shot route handoff — the frozen facts go to the
    // result route while the record write keeps running in the store, never awaited here.
    val completed = controller.session?.takeIf { it.isComplete }
    LaunchedEffect(completed) {
        if (completed != null) onSolved(completed.elapsedMs, completed.hintsUsed)
    }

    Box(modifier.fillMaxSize()) {
        AdaptivePlayScaffold(
            layout = layout,
            modifier = Modifier.fillMaxSize(),
            board = { BoardArea(controller, status, onRetry) },
            side = {
                PlayChrome(
                    title = "Zip",
                    subtitle = if (ready == null) "Daily" else subtitle(controller),
                    clock = controller,
                    hints = controller,
                    undo = controller,
                    banner = ready?.banner,
                    feedback = controller,
                )
            },
        )
    }
}

/** The board pane: Zip's one board once a puzzle is ready, the shared fallback otherwise. */
@Composable
private fun BoxScope.BoardArea(
    controller: ZipPlayController,
    status: PlayBoardStatus,
    onRetry: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .testTag("play.board_area")
            // A4: standard pane margins; the cap below is a max, not a fixed width.
            .padding(horizontal = Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        if (status is PlayBoardStatus.Ready && controller.session != null) {
            ZipBoard(controller, Modifier)
        } else {
            PlayBoardFallback(status, onRetry, tagPrefix = "play")
        }
    }
}

/**
 * Zip's one board, chrome-free ([GamePlayUi.Board]): the daily pane and the tutorial host
 * both draw it, so a lesson's trace input is the daily one. Draws nothing until
 * [controller] holds a session.
 */
@Composable
private fun ZipBoard(controller: ZipPlayController, modifier: Modifier) {
    val rejected = controller.feedback.rejectedMove
    val model = remember(controller, controller.session, controller.hintCell, rejected) {
        controller.rendererModel()
    }
    val puzzle = controller.session?.puzzle
    if (model == null || puzzle == null) return
    val reducedMotion = rememberReducedMotion()
    // Ticket 28: the hint arrow lives ~2s at the auto-placed head (or until
    // the next input, which nulls it), fading out unless motion is reduced.
    val arrow = controller.hintArrow
    val arrowAlpha = remember(arrow) { Animatable(1f) }
    LaunchedEffect(arrow) {
        if (arrow == null) return@LaunchedEffect
        delay(HINT_ARROW_HOLD_MS)
        if (!reducedMotion) {
            arrowAlpha.animateTo(0f, tween(HINT_ARROW_FADE_MS))
        }
        controller.clearHintArrow()
    }
    Box(modifier) {
        DragTraceBoard(
            rows = model.n,
            cols = model.n,
            path = model.path.map { GridCell(it / model.n, it % model.n) },
            hooks = remember(puzzle) { ZipDragHooks(puzzle.board) },
            onEvent = controller::onDragEvent,
            edgeOverlays = zipWallOverlays(model),
            underlay = { ZipPipes(model) },
            cellContent = { row, col ->
                ZipCellDecor(model, row, col, animateFlash = !reducedMotion)
            },
            // A4: capped at PlayBoardMaxWidth and centered by the pane; BoardGrid's
            // aspectRatio keeps the grid exact under these loose constraints. The
            // outline rides the same modifier (U1 §4 — Zip's outline is gridline-weight).
            modifier = Modifier
                .widthIn(max = PlayBoardMaxWidth)
                .heightIn(max = PlayBoardMaxWidth)
                .zipBoardOutline(model.n)
                .testTag("play.board"),
        )
        arrow?.let { HintArrowHead(it, model, arrowAlpha.value, Modifier.matchParentSize()) }
    }
}

private fun subtitle(controller: ZipPlayController): String =
    controller.session?.ref?.puzzleNumber?.let { number -> "Daily · #$number" } ?: "Daily"
