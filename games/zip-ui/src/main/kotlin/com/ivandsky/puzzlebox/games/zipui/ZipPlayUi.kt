package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.AdaptivePlayScaffold
import com.ivandsky.puzzlebox.gameui.DragTraceBoard
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.PlayActionBar
import com.ivandsky.puzzlebox.gameui.PlayBoardFallback
import com.ivandsky.puzzlebox.gameui.PlayBoardMaxWidth
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayChrome
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayClear
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.PlaySolveFrame
import com.ivandsky.puzzlebox.gameui.playBoardPane
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.delay

/** Zip's one play registration (ADR-0001): its controller, its board and its play screen. */
class ZipPlayUi : GamePlayUi {
    override val gameId: GameId get() = ZipDefinition.id

    override val brand: GameBrand get() = ZipBrand

    override fun createController(env: PlayEnvironment): GamePlayController = ZipPlayController(env)

    override val tutorial: List<TutorialStep> get() = ZipTutorial.steps

    @Composable
    override fun Board(controller: GamePlayController, modifier: Modifier) {
        // The tutorial host draws no toolbar pane, so the board keeps Zip's own cap.
        ZipBoard(controller as ZipPlayController, paneCap = null, modifier)
    }

    /** Clear empties the trace to the start state, one undo unit (R1). */
    override fun clearFor(controller: GamePlayController): PlayClear = (controller as ZipPlayController).edits

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
        ZipPlayScreen(controller as ZipPlayController, status, layout, sidePanel, onRetry, onSolved, modifier)
    }
}

/**
 * The Zip play screen (ticket 13, A14): the board under the route's top bar, then the
 * shared caption and action bar (Clear empties the trace), arranged by the 07 adaptive
 * policy (two-pane needs Expanded width AND landscape). State flows one way:
 * [controller] owns the session; the screen draws and forwards events.
 *
 * Ticket 23: the win moment is a ROUTE handoff, not an overlay. When the session
 * completes, the board plays its [PlaySolveFrame] moment, then [onSolved] carries the
 * frozen A3 facts to the result route; this screen never renders win content itself.
 */
@Composable
fun ZipPlayScreen(
    controller: ZipPlayController,
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
            board = { paneCap -> BoardArea(controller, status, paneCap, onRetry, onSolved) },
            side = {
                PlayChrome(
                    clock = controller,
                    hints = controller,
                    banner = ready?.banner,
                    feedback = controller,
                )
            },
            actions = { PlayActionBar(controller, controller, controller.edits, ZipBrand.accent) },
            sidePanel = sidePanel,
        )
    }
}

/** The board pane: Zip's one board once a puzzle is ready, the shared fallback otherwise. */
@Composable
private fun BoxScope.BoardArea(
    controller: ZipPlayController,
    status: PlayBoardStatus,
    paneCap: Dp?,
    onRetry: () -> Unit,
    onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
) {
    Box(
        Modifier
            .playBoardPane()
            .testTag("play.board_area")
            // A4: standard pane margins; the cap below is a max, not a fixed width.
            .padding(horizontal = Spacing.lg),
        contentAlignment = Alignment.TopCenter,
    ) {
        val session = controller.session
        if (status is PlayBoardStatus.Ready && session != null) {
            val n = session.puzzle.board.n
            // The sweep follows Zip's rounded outline (U1 §4).
            val shape = remember(n) {
                GenericShape { size, _ ->
                    val radius = size.minDimension / n * ZipBoardSpec.boardOutlineRadiusFraction
                    addRoundRect(RoundRect(size.toRect(), CornerRadius(radius)))
                }
            }
            // The win moment (ticket 23): a one-shot route handoff after the solve frame;
            // the record write keeps running in the store, never awaited here.
            PlaySolveFrame(
                brand = ZipBrand,
                solved = session.completedAt,
                onHandOff = {
                    controller.session?.takeIf { it.isComplete }?.let { onSolved(it.elapsedMs, it.hintsUsed) }
                },
                shape = shape,
            ) {
                ZipBoard(controller, paneCap, Modifier)
            }
        } else {
            PlayBoardFallback(status, onRetry, tagPrefix = "play")
        }
    }
}

/**
 * Zip's one board, chrome-free ([GamePlayUi.Board]): the daily pane and the tutorial host
 * both draw it, so a lesson's trace input is the daily one. Capped at [paneCap] in a
 * two-pane toolbar pane, [PlayBoardMaxWidth] otherwise. Draws nothing until [controller]
 * holds a session.
 */
@Composable
private fun ZipBoard(controller: ZipPlayController, paneCap: Dp?, modifier: Modifier) {
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
        delay(MotionTokens.HintHoldMillis)
        if (!reducedMotion) {
            arrowAlpha.animateTo(0f, tween(MotionTokens.HintFadeDuration))
        }
        controller.clearHintArrow()
    }
    val hooks = remember(puzzle) { ZipDragHooks(puzzle.board) }
    // Snapshot state read only inside ZipPipes' draw: a moving finger redraws, never recomposes.
    val finger = remember { mutableStateOf<Offset?>(null) }
    val cap = paneCap ?: PlayBoardMaxWidth
    Box(modifier) {
        DragTraceBoard(
            rows = model.n,
            cols = model.n,
            path = model.path.map { GridCell(it / model.n, it % model.n) },
            hooks = hooks,
            onEvent = controller::onDragEvent,
            edgeOverlays = zipWallOverlays(model),
            underlay = { ZipPipes(model, { finger.value }, hooks, animate = !reducedMotion) },
            cellContent = { row, col ->
                ZipCellDecor(model, row, col, animated = !reducedMotion)
            },
            onPointer = { finger.value = it },
            // A4: capped at PlayBoardMaxWidth (a two-pane board fills its pane, A15) and
            // centered by the pane; BoardGrid's aspectRatio keeps the grid exact under
            // these loose constraints. The outline rides the same modifier (U1 §4 — Zip's
            // outline is gridline-weight).
            modifier = Modifier
                .widthIn(max = cap)
                .heightIn(max = cap)
                .zipBoardOutline(model.n)
                .testTag("play.board"),
        )
        arrow?.let { HintArrowHead(it, model, arrowAlpha.value, Modifier.matchParentSize()) }
    }
}
