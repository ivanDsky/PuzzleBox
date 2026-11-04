package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.ivandsky.puzzlebox.gameui.BoardError
import com.ivandsky.puzzlebox.gameui.BoardLoading
import com.ivandsky.puzzlebox.gameui.PlayChrome
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.games.queensui.QueensPlayBoard
import com.ivandsky.puzzlebox.games.queensui.QueensPlayControls
import com.ivandsky.puzzlebox.games.queensui.queensBoardSideFor

/**
 * The Queens play screen (queens-10): the queens-08 interactive board + control bar,
 * arranged by the same 07 adaptive policy as Zip. The win presentation is Zip's: the
 * solved transition hands the frozen result facts (time, hints) to the caller's
 * [onSolved] — the app navigates to the opaque result ROUTE (fix round 6; the modal
 * overlay is deleted). The record write already ran in the flow.
 */
@Composable
fun QueensPlayScreen(
    state: QueensPlayState,
    layout: PlayLayout,
    onBack: () -> Unit,
    onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Win → result route (Zip's handoff): ONE navigation trigger per completion —
    // keyed on the completedAt instance so a replay (new completion) hands off again.
    LaunchedEffect(state.board?.session?.completedAt) {
        val completed = state.board?.session?.takeIf { it.isComplete } ?: return@LaunchedEffect
        onSolved(completed.elapsedMs, completed.hintsUsed)
    }
    Box(modifier.fillMaxSize()) {
        AdaptivePlayScaffold(
            layout = layout,
            modifier = Modifier.fillMaxSize(),
            board = { BoardArea(state) },
            side = { SidePanel(state) },
        )
    }
}

@Composable
private fun BoxScope.BoardArea(state: QueensPlayState) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .testTag("queens.board_area"),
        contentAlignment = Alignment.Center,
    ) {
        val ready = state.screen as? QueensScreen.Ready
        val boardState = state.board
        when {
            ready != null && boardState != null -> {
                // §6 sizing policy: the board side is computed — pane space after chrome,
                // capped by cellMaxSize per cell, floored by cellMinSize per cell. The
                // horizontal pane padding is the chrome reserved here; the side panel is
                // already excluded by the scaffold's board-pane weight.
                val side = queensBoardSideFor(
                    paneWidth = maxWidth,
                    paneHeight = maxHeight,
                    reservedTop = 0.dp,
                    reservedBottom = 0.dp,
                    cellsPerSide = ready.puzzle.size,
                    panePadding = Spacing.lg * 2,
                )
                QueensPlayBoard(
                    state = boardState,
                    modifier = Modifier
                        .size(side) // side already reserves the pane padding via queensBoardSideFor
                        .testTag("queens.board"),
                )
            }

            state.screen is QueensScreen.Unavailable -> BoardError(
                message = (state.screen as QueensScreen.Unavailable).message,
                onRetry = state::load,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.xl)
                    .testTag("queens.error"),
            )

            else -> BoardLoading(modifier = Modifier.testTag("queens.loading"))
        }
    }
}

@Composable
private fun SidePanel(state: QueensPlayState) {
    val ready = state.screen as? QueensScreen.Ready
    PlayChrome(
        title = "Queens",
        subtitle = ready?.ref?.puzzleNumber?.let { "Daily · #$it" } ?: "Daily",
        clock = state,
        hints = state,
        undo = state,
        banner = ready?.degraded?.let(::degradeBannerMessage),
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        state.board?.let { QueensPlayControls(state = it) }
    }
}
