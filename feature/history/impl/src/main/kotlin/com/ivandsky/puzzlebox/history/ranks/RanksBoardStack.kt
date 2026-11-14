package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel

/**
 * The hub's swipe area, one for the phone and the tablet: the viewer's solve of the day, then the board, re-ranked in
 * place by [scrub] as a horizontal swipe anywhere on the stack moves the hub to the next board. [onRowClick] is a tap
 * on another player's row; null and rows take no tap.
 *
 * What differs between the two layouts is said here and nowhere else. [contentPadding] insets the content inside the
 * swipe area, so a swipe still starts at the pane's edge. [hero] is drawn above the solve. With [dotsPulse] the page
 * dots sit between the solve and the board, the current one scaled by it; without, there are no dots.
 */
@Composable
internal fun RanksBoardStack(
    state: RanksUiState,
    scrub: BoardScrub,
    actions: RanksActions,
    onRowClick: ((DailyBoardRowModel) -> Unit)?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    dotsPulse: (() -> Float)? = null,
    hero: (@Composable () -> Unit)? = null,
) {
    BoardSwipe(scrub, state.position, state.boardCount, actions.onSwipe, modifier.fillMaxWidth()) {
        Column(Modifier.padding(contentPadding)) {
            hero?.invoke()
            YourSolveRow(state.yourSolve, actions.onPlayAgain, actions.onOpenReplay, actions.onCompare)
            if (dotsPulse != null) {
                PageDots(
                    state.boardCount,
                    state.position,
                    Modifier.align(Alignment.CenterHorizontally).padding(bottom = Spacing.md).testTag("ranks.dots"),
                    pulseScale = dotsPulse,
                    scrub = scrub,
                )
            }
            RanksBoardSlot(state.board, state.signIn, actions.onSignIn, onRowClick = onRowClick, morph = rememberBoardMorph(state, scrub))
        }
    }
}
