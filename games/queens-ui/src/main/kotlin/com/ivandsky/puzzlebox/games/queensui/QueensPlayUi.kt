package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.AdaptivePlayScaffold
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardFallback
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayChrome
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.model.GameId

/** Queens' one play registration (ADR-0001): its controller and its play screen. */
class QueensPlayUi : GamePlayUi {
    override val gameId: GameId get() = QueensDefinition.id

    override fun createController(env: PlayEnvironment): GamePlayController = QueensPlayController(env)

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

/**
 * The Queens play screen (queens-10): the queens-08 interactive board + control bar,
 * arranged by the same 07 adaptive policy as Zip. The win presentation is Zip's: the
 * solved transition hands the frozen result facts (time, hints) to the caller's
 * [onSolved] — the app navigates to the opaque result ROUTE (fix round 6; the modal
 * overlay is deleted). The record write already runs in the play feature's store.
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
    // Win → result route (Zip's handoff): ONE navigation trigger per completion —
    // keyed on the completedAt instance so a replay (new completion) hands off again.
    LaunchedEffect(controller.board?.session?.completedAt) {
        val completed = controller.board?.session?.takeIf { it.isComplete } ?: return@LaunchedEffect
        onSolved(completed.elapsedMs, completed.hintsUsed)
    }
    Box(modifier.fillMaxSize()) {
        AdaptivePlayScaffold(
            layout = layout,
            modifier = Modifier.fillMaxSize(),
            board = { BoardArea(controller, status, onRetry) },
            side = { SidePanel(controller, status) },
        )
    }
}

@Composable
private fun BoxScope.BoardArea(controller: QueensPlayController, status: PlayBoardStatus, onRetry: () -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .testTag("queens.board_area"),
        contentAlignment = Alignment.Center,
    ) {
        val boardState = controller.board
        if (status is PlayBoardStatus.Ready && boardState != null) {
            // §6 sizing policy: the board side is computed — pane space after chrome,
            // capped by cellMaxSize per cell, floored by cellMinSize per cell. The
            // horizontal pane padding is the chrome reserved here; the side panel is
            // already excluded by the scaffold's board-pane weight.
            val side = queensBoardSideFor(
                paneWidth = maxWidth,
                paneHeight = maxHeight,
                reservedTop = 0.dp,
                reservedBottom = 0.dp,
                cellsPerSide = boardState.session.puzzle.size,
                panePadding = Spacing.lg * 2,
            )
            QueensPlayBoard(
                state = boardState,
                modifier = Modifier
                    .size(side) // side already reserves the pane padding via queensBoardSideFor
                    .testTag("queens.board"),
            )
        } else {
            PlayBoardFallback(status, onRetry, tagPrefix = "queens")
        }
    }
}

@Composable
private fun SidePanel(controller: QueensPlayController, status: PlayBoardStatus) {
    val ready = status as? PlayBoardStatus.Ready
    PlayChrome(
        title = "Queens",
        subtitle = ready?.let { controller.board?.session?.ref?.puzzleNumber }?.let { "Daily · #$it" } ?: "Daily",
        clock = controller,
        hints = controller,
        undo = controller,
        banner = ready?.banner,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        controller.board?.let { QueensPlayControls(state = it) }
    }
}
