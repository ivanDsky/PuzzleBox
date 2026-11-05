package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.gameui.AdaptivePlayScaffold
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardFallback
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayChrome
import com.ivandsky.puzzlebox.gameui.PlayClear
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.PlayOverflowItem
import com.ivandsky.puzzlebox.gameui.PlaySolveFrame
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.gameui.playBoardPane
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.game.RendererModel
import com.ivandsky.puzzlebox.gameui.BoardPreview
import com.ivandsky.puzzlebox.games.queens.render.QueensRendererModel

/** Queens' one play registration (ADR-0001): its controller, its board and its play screen. */
class QueensPlayUi : GamePlayUi {
    override val gameId: GameId get() = QueensDefinition.id

    override val brand: GameBrand get() = QueensBrand

    override fun createController(env: PlayEnvironment): GamePlayController = QueensPlayController(env)

    override val tutorial: List<TutorialStep> get() = QueensTutorial.steps

    @Composable
    override fun Board(controller: GamePlayController, modifier: Modifier) {
        QueensBoard(controller as QueensPlayController, modifier)
    }

    override fun previewModel(preview: BoardPreview): RendererModel? = queensPreviewModel(preview)

    @Composable
    override fun BoardThumbnail(model: RendererModel, modifier: Modifier) {
        (model as? QueensRendererModel)?.let { QueensBoardThumbnail(it, modifier) }
    }

    /**
     * Clear wipes the whole board as one command (fix round 6). Round-7 ruling: it is live
     * when ANYTHING can be wiped, any queen OR any mark (a crosses-only board clears).
     */
    override fun clearFor(controller: GamePlayController): PlayClear = QueensClear(controller as QueensPlayController)

    /** A14: the Auto-cross setting (ON auto-fills exclusion marks, OFF places queens bare). */
    override fun overflowFor(controller: GamePlayController): List<PlayOverflowItem> {
        val board = (controller as QueensPlayController).board ?: return emptyList()
        return listOf(PlayOverflowItem("Auto-cross", checked = board.autoCross) { board.setAutoCross(!board.autoCross) })
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
        QueensPlayScreen(controller as QueensPlayController, status, layout, onRetry, onSolved, modifier)
    }
}

/** Queens' Clear over whatever board [controller] holds now. */
private class QueensClear(private val controller: QueensPlayController) : PlayClear {
    override val canClear: Boolean
        get() = controller.board?.session?.let { !it.isComplete && (it.queens.isNotEmpty() || it.marks.isNotEmpty()) } == true

    override fun clear() {
        controller.board?.clearBoard()
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
    onRetry: () -> Unit,
    onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = status as? PlayBoardStatus.Ready
    Box(modifier.fillMaxSize()) {
        AdaptivePlayScaffold(
            layout = layout,
            modifier = Modifier.fillMaxSize(),
            board = { BoardArea(controller, status, onRetry, onSolved) },
            side = {
                PlayChrome(
                    clock = controller,
                    hints = controller,
                    undo = controller,
                    clear = remember(controller) { QueensClear(controller) },
                    banner = ready?.banner,
                    feedback = controller,
                )
            },
        )
    }
}

/** The board pane: Queens' one board once a puzzle is ready, the shared fallback otherwise. */
@Composable
private fun BoxScope.BoardArea(
    controller: QueensPlayController,
    status: PlayBoardStatus,
    onRetry: () -> Unit,
    onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
) {
    val board = controller.board
    Box(
        Modifier
            .playBoardPane()
            .testTag("queens.board_area")
            // The daily pane's §6 gutter lives here, not in the board: the tutorial host
            // pads its own board pane, as it does for every game. Sideways only, so the
            // board's top lines up with every game's (A14).
            .padding(horizontal = board?.let { queensPanePaddingFor(it.session.puzzle.size) / 2 } ?: 0.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (status is PlayBoardStatus.Ready && board != null) {
            // Win → result route (Zip's handoff): ONE hand-off per completion, after the
            // solve frame, keyed on the completedAt instance so a replay hands off again.
            PlaySolveFrame(
                brand = QueensBrand,
                solved = board.session.completedAt,
                onHandOff = {
                    controller.board?.session?.takeIf { it.isComplete }?.let { onSolved(it.elapsedMs, it.hintsUsed) }
                },
            ) {
                QueensBoard(controller, Modifier)
            }
        } else {
            PlayBoardFallback(status, onRetry, tagPrefix = "queens")
        }
    }
}

/**
 * Queens' one board, chrome-free ([GamePlayUi.Board]): the daily pane and the tutorial
 * host both draw it, so a lesson's tap cycle is the daily one. It fills the space it is
 * given up to the §6 cap — the caller owns any gutter — and draws nothing until
 * [controller] holds a session.
 */
@Composable
private fun QueensBoard(controller: QueensPlayController, modifier: Modifier) {
    val boardState = controller.board ?: return
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // §6 sizing policy: the board side is computed — the space given, capped by
        // queensBoardMaxSideFor (64dp per cell on a daily, PlayBoardMaxWidth on a
        // lesson), never above the pane. The side panel is already excluded by the
        // scaffold's board-pane weight.
        val side = queensBoardSideFor(
            paneWidth = maxWidth,
            paneHeight = maxHeight,
            reservedTop = 0.dp,
            reservedBottom = 0.dp,
            cellsPerSide = boardState.session.puzzle.size,
            panePadding = 0.dp,
        )
        QueensPlayBoard(
            state = boardState,
            modifier = Modifier
                .size(side)
                .testTag("queens.board"),
        )
    }
}
