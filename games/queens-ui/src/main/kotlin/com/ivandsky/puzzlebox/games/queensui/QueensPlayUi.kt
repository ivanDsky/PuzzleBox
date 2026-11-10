package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.AdaptivePlayScaffold
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayActionBar
import com.ivandsky.puzzlebox.gameui.PlayBoardFallback
import com.ivandsky.puzzlebox.gameui.PlayBoardPane
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayChrome
import com.ivandsky.puzzlebox.gameui.PlayClear
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.PlayOverflowItem
import com.ivandsky.puzzlebox.gameui.PlaySolveFrame
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.delay

/** Queens' one play registration (ADR-0001): its controller, its board and its play screen. */
class QueensPlayUi(
    private val settings: QueensSettings,
    private val hintMode: HintMode.Stepwise,
) : GamePlayUi {
    override val gameId: GameId get() = QueensDefinition.id

    override val brand: GameBrand get() = QueensBrand

    override fun createController(env: PlayEnvironment): GamePlayController =
        QueensPlayController(env, settings, hintMode = hintMode)

    /** A lesson teaches the rules with Auto-cross on, whatever the player chose for their own puzzles. */
    override fun createLessonController(env: PlayEnvironment): GamePlayController =
        QueensPlayController(env, settings = null, hintMode = hintMode)

    override val tutorial: List<TutorialStep> get() = QueensTutorial.steps

    @Composable
    override fun Board(controller: GamePlayController, modifier: Modifier) {
        // The tutorial host draws no toolbar pane, so the board keeps Queens' own cap.
        QueensBoard(controller as QueensPlayController, paneCap = null, modifier)
    }

    /**
     * Clear wipes the whole board as one command (fix round 6). It is ALWAYS enabled: a
     * button that flipped disabled->enabled on the first queen read as jumpy, and clearing
     * an empty or solved board is a no-op (no undo entry, no session write).
     */
    override fun clearFor(controller: GamePlayController): PlayClear =
        PlayClear { (controller as QueensPlayController).board?.clearBoard() }

    /** A14: the Auto-cross setting (ON auto-fills exclusion marks, OFF places queens bare), saved for every Queens puzzle. */
    override fun overflowFor(controller: GamePlayController): List<PlayOverflowItem> {
        val queens = controller as QueensPlayController
        if (queens.board == null) return emptyList()
        return listOf(PlayOverflowItem("Auto-cross", checked = queens.autoCross) { queens.chooseAutoCross(!queens.autoCross) })
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
        QueensPlayScreen(controller as QueensPlayController, status, layout, sidePanel, onRetry, onSolved, modifier)
    }
}

/**
 * The Queens play screen (queens-10, A14): the queens-08 interactive board under the
 * route's top bar, then the shared caption and action bar, arranged by the same 07
 * adaptive policy as Zip. The win presentation is Zip's: the board plays its
 * [PlaySolveFrame] moment, then the solved transition hands the frozen result facts
 * (time, hints) to the caller's [onSolved]; the app navigates to the opaque result ROUTE
 * (fix round 6). The record write already runs in the play feature's store.
 */
@Composable
fun QueensPlayScreen(
    controller: QueensPlayController,
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
            actions = {
                PlayActionBar(controller, controller, remember(controller) { PlayClear { controller.board?.clearBoard() } }, QueensBrand.accent)
            },
            sidePanel = sidePanel,
        )
    }
}

/** The board pane: Queens' one board once a puzzle is ready, the shared fallback otherwise. */
@Composable
private fun BoxScope.BoardArea(
    controller: QueensPlayController,
    status: PlayBoardStatus,
    paneCap: Dp?,
    onRetry: () -> Unit,
    onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
) {
    val board = controller.board
    PlayBoardPane(
        Modifier
            .testTag("queens.board_area")
            // The daily pane's §6 gutter lives here, not in the board: the tutorial host
            // pads its own board pane, as it does for every game. Sideways only; the
            // shared pane owns the vertical centring.
            .padding(horizontal = board?.let { queensPanePaddingFor(it.session.puzzle.size) / 2 } ?: 0.dp),
    ) {
        if (status is PlayBoardStatus.Ready && board != null) {
            // The live solve's orchestration (A13) owns the board first: the frame sees the
            // completion once the queens have started their Pulse. A board restored solved
            // was never live, so it hands off at once.
            val completedAt = board.session.completedAt
            val reducedMotion = rememberReducedMotion()
            var framed by remember(board) { mutableStateOf(completedAt) }
            LaunchedEffect(completedAt) {
                if (completedAt != null && framed != completedAt) {
                    delay(QueensMotion.solveLeadMs(board.session.queens.size, reducedMotion))
                }
                framed = completedAt
            }
            // Win → result route (Zip's handoff): ONE hand-off per completion, after the
            // solve frame, keyed on the completedAt instance so a replay hands off again.
            PlaySolveFrame(
                brand = QueensBrand,
                solved = framed,
                onHandOff = {
                    controller.board?.session?.takeIf { it.isComplete }?.let { onSolved(it.elapsedMs, it.hintsUsed) }
                },
            ) {
                QueensBoard(controller, paneCap, Modifier)
            }
        } else {
            PlayBoardFallback(status, onRetry, tagPrefix = "queens")
        }
    }
}

/**
 * Queens' one board, chrome-free ([GamePlayUi.Board]): the daily pane and the tutorial
 * host both draw it, so a lesson's tap cycle is the daily one. It fills the space it is
 * given up to the §6 cap — [paneCap] in a two-pane toolbar pane, the caller owns any
 * gutter — and draws nothing until [controller] holds a session.
 */
@Composable
private fun QueensBoard(controller: QueensPlayController, paneCap: Dp?, modifier: Modifier) {
    val boardState = controller.board ?: return
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // §6 sizing policy: the board side is computed — the space given, capped by
        // queensBoardMaxSideFor (64dp per cell on a daily, PlayBoardMaxWidth on a
        // lesson), never above the pane. A two-pane board fills its pane instead (A15).
        val side = queensBoardSideFor(
            paneWidth = maxWidth,
            paneHeight = maxHeight,
            reservedTop = 0.dp,
            reservedBottom = 0.dp,
            cellsPerSide = boardState.session.puzzle.size,
            panePadding = 0.dp,
            maxSide = paneCap ?: queensBoardMaxSideFor(boardState.session.puzzle.size),
        )
        QueensPlayBoard(
            state = boardState,
            modifier = Modifier
                .size(side)
                .testTag("queens.board"),
        )
    }
}
