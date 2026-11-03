package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.AdaptivePlayScaffold
import com.ivandsky.puzzlebox.gameui.BoardError
import com.ivandsky.puzzlebox.gameui.BoardLoading
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.games.queensui.QueensPlayBoard
import com.ivandsky.puzzlebox.games.queensui.QueensPlayControls
import com.ivandsky.puzzlebox.games.queensui.QueensWinOverlay
import com.ivandsky.puzzlebox.games.queensui.queensBoardSideFor

/**
 * The Queens play screen (queens-10): the queens-08 interactive board + control bar,
 * arranged by the same 07 adaptive policy as Zip. No new UI vocabulary — the win
 * presentation is the board's own overlay (§5.6) and the record write already ran in
 * the flow, so there is no result-route handoff here (QueensSession v1 carries no
 * timer/hint counters to freeze onto the result route).
 */
@Composable
fun QueensPlayScreen(
    state: QueensPlayState,
    layout: PlayLayout,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        AdaptivePlayScaffold(
            layout = layout,
            modifier = Modifier.fillMaxSize(),
            board = { BoardArea(state) },
            side = { SidePanel(state) },
        )
        // R-3 (fix round 4): the solved state presents FULLSCREEN over the play
        // surface — rendered above the scaffold at this level, the scrim + banner
        // cover the board pane and the side panel alike (inside the board pane the
        // overlay inherited the board's BoxWithConstraints size and the game chrome
        // visibly cut it off). No game chrome shows through or beside it.
        val board = state.board
        if (board != null && board.session.isComplete) {
            QueensWinOverlay(state = board, onPlayAgain = { state.replay() })
        }
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
    val boardState = state.board
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .testTag("queens.side_panel"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Queens",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle(ready),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ready?.degraded?.let { error ->
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("queens.degrade_banner"),
            ) {
                Text(
                    text = degradeBannerMessage(error),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            }
        }

        if (boardState != null) {
            QueensPlayControls(state = boardState)
        }
    }
}

private fun subtitle(ready: QueensScreen.Ready?): String = when {
    ready == null -> "Daily"
    else -> ready.ref?.puzzleNumber?.let { number -> "Daily · #$number" } ?: "Daily"
}
